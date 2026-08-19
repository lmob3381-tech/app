"""
Service untuk komunikasi dengan GitHub API:
- trigger workflow (repository_dispatch)
- cek status run terbaru
- ambil artifact hasil build
"""
import asyncio
import httpx
from app import config

GITHUB_API = "https://api.github.com"


def _headers() -> dict:
    return {
        "Authorization": f"Bearer {config.GITHUB_PAT}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


async def validate_repo_and_pat() -> tuple[bool, str]:
    """Cek apakah PAT valid dan punya akses ke repo yang dikonfigurasi."""
    if not config.GITHUB_PAT or not config.GITHUB_REPO:
        return False, "GitHub belum dikonfigurasi (PAT atau repo kosong)."
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}"
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(url, headers=_headers(), timeout=15)
        except httpx.HTTPError as e:
            return False, f"Gagal menghubungi GitHub: {e}"
    if resp.status_code == 200:
        return True, "OK"
    if resp.status_code == 404:
        return False, "Repository tidak ditemukan atau PAT tidak punya akses."
    if resp.status_code == 401:
        return False, "PAT tidak valid / kadaluarsa."
    return False, f"GitHub API error: {resp.status_code}"


async def dispatch_workflow(workflow_file: str, inputs: dict) -> tuple[bool, str]:
    """
    Trigger GitHub Actions workflow via workflow_dispatch.
    workflow_file contoh: 'web-to-apk.yml'
    """
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/workflows/{workflow_file}/dispatches"
    payload = {"ref": "main", "inputs": inputs}
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.post(url, headers=_headers(), json=payload, timeout=15)
        except httpx.HTTPError as e:
            return False, f"Gagal menghubungi GitHub: {e}"
    if resp.status_code == 204:
        return True, "Workflow berhasil dijalankan."
    return False, f"Gagal trigger workflow: {resp.status_code} {resp.text[:200]}"


async def dispatch_workflow_on_branch(workflow_file: str, branch: str, inputs: dict) -> tuple[bool, str]:
    """
    Sama seperti dispatch_workflow, tapi menjalankan workflow yang ada di
    'branch' tertentu (dipakai untuk Source -> APK yang source-nya
    di-push ke branch temporary, bukan branch default).

    Catatan penting: GitHub hanya mengizinkan workflow_dispatch pada suatu
    'ref' kalau file workflow tersebut JUGA ada di branch default repo.
    Branch temporary hanya perlu punya source project-nya, bukan file
    workflow-nya sendiri.

    Retry karena ref branch baru kadang belum full-propagated saat dispatch
    pertama dicoba tepat setelah create_temp_branch + upload_source_zip.
    """
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/workflows/{workflow_file}/dispatches"
    payload = {"ref": branch, "inputs": inputs}

    last_status, last_text = None, ""
    for attempt in range(4):
        async with httpx.AsyncClient() as client:
            try:
                resp = await client.post(url, headers=_headers(), json=payload, timeout=15)
            except httpx.HTTPError as e:
                last_status, last_text = "conn_error", str(e)
                await asyncio.sleep(2)
                continue
        if resp.status_code == 204:
            return True, "Workflow berhasil dijalankan."
        last_status, last_text = resp.status_code, resp.text[:200]
        if resp.status_code in (404, 422) and attempt < 3:
            await asyncio.sleep(2)
            continue
        break

    return False, f"Gagal trigger workflow: {last_status} {last_text}"


async def get_latest_run(workflow_file: str) -> dict | None:
    """Ambil run terbaru dari workflow tertentu."""
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/workflows/{workflow_file}/runs"
    params = {"per_page": 1}
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(url, headers=_headers(), params=params, timeout=15)
        except httpx.HTTPError:
            return None
    if resp.status_code != 200:
        return None
    runs = resp.json().get("workflow_runs", [])
    return runs[0] if runs else None


async def get_run_status(run_id: int) -> dict | None:
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/runs/{run_id}"
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(url, headers=_headers(), timeout=15)
        except httpx.HTTPError:
            return None
    if resp.status_code != 200:
        return None
    return resp.json()


async def list_artifacts(run_id: int) -> list:
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/runs/{run_id}/artifacts"
    async with httpx.AsyncClient() as client:
        try:
            resp = await client.get(url, headers=_headers(), timeout=15)
        except httpx.HTTPError:
            return []
    if resp.status_code != 200:
        return []
    return resp.json().get("artifacts", [])


async def download_artifact(artifact_id: int, dest_path: str) -> bool:
    """Download artifact ZIP dari GitHub ke path lokal."""
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/actions/artifacts/{artifact_id}/zip"
    async with httpx.AsyncClient() as client:
        try:
            async with client.stream("GET", url, headers=_headers(), timeout=60, follow_redirects=True) as resp:
                if resp.status_code != 200:
                    return False
                with open(dest_path, "wb") as f:
                    async for chunk in resp.aiter_bytes():
                        f.write(chunk)
        except httpx.HTTPError:
            return False
    return True
