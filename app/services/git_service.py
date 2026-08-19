"""
Push source project ke branch temporary di GitHub repo,
supaya GitHub Actions bisa checkout dan build dari situ.

Strategi: source di-zip ulang, lalu di-commit sebagai satu file biner
(build-sources/<build_id>.zip) ke branch temporary lewat GitHub Contents API.
Workflow source-to-apk.yml akan unzip file itu sebelum build.

Kenapa begini (bukan push per-file):
- Tidak perlu install `git` CLI di server Debian
- Cukup 1-2 API call per build, bukan ratusan (build besar tetap ringan)
- Menghindari commit ribuan file kecil yang mengotori riwayat repo
"""
import asyncio
import base64
import httpx
from app import config

GITHUB_API = "https://api.github.com"

# GitHub Contents API (base64 PUT) tolak file besar; di atas ini kirim error 422/413.
# Batas praktis aman jauh di bawah limit resmi GitHub (~100MB) karena payload jadi
# base64 (naik ~33%) plus overhead JSON.
MAX_CONTENTS_API_ZIP_SIZE = 60 * 1024 * 1024  # 60 MB


def _headers() -> dict:
    return {
        "Authorization": f"Bearer {config.GITHUB_PAT}",
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
    }


async def _get_default_branch_sha() -> tuple[str | None, str | None]:
    """Ambil SHA commit terbaru di branch default (biasanya main)."""
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}"
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=_headers(), timeout=15)
    if resp.status_code != 200:
        return None, None
    default_branch = resp.json().get("default_branch", "main")

    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/git/ref/heads/{default_branch}"
    async with httpx.AsyncClient() as client:
        resp = await client.get(url, headers=_headers(), timeout=15)
    if resp.status_code != 200:
        return None, default_branch
    sha = resp.json().get("object", {}).get("sha")
    return sha, default_branch


async def create_temp_branch(build_id: str) -> tuple[bool, str]:
    """Buat branch baru 'build/<build_id>' dari branch default."""
    sha, default_branch = await _get_default_branch_sha()
    if not sha:
        return False, f"Gagal mengambil branch default ({default_branch})."

    branch_name = f"build/{build_id}"
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/git/refs"
    payload = {"ref": f"refs/heads/{branch_name}", "sha": sha}
    async with httpx.AsyncClient() as client:
        resp = await client.post(url, headers=_headers(), json=payload, timeout=15)

    if resp.status_code != 201:
        return False, f"Gagal membuat branch: {resp.status_code} {resp.text[:200]}"

    # GitHub kadang butuh sesaat sebelum ref baru konsisten di semua node
    # (terutama untuk endpoint workflow_dispatch yang butuh 'ref' valid).
    # Poll singkat supaya create_temp_branch tidak mengembalikan sukses
    # padahal branch belum benar-benar bisa dipakai downstream.
    ref_url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/git/ref/heads/{branch_name}"
    for _ in range(5):
        async with httpx.AsyncClient() as client:
            check = await client.get(ref_url, headers=_headers(), timeout=10)
        if check.status_code == 200:
            break
        await asyncio.sleep(1)

    return True, branch_name


async def upload_source_zip(branch_name: str, build_id: str, zip_path: str) -> tuple[bool, str]:
    """Upload file zip source ke branch temporary via GitHub Contents API.

    Retry singkat kalau GitHub sempat balas 404/409 (ref belum ke-propagate
    penuh walau create_temp_branch sudah konfirmasi 201), dan validasi ukuran
    file supaya gagalnya jelas ("source terlalu besar") bukan error API mentah.
    """
    import os
    file_size = os.path.getsize(zip_path)
    if file_size > MAX_CONTENTS_API_ZIP_SIZE:
        size_mb = file_size / (1024 * 1024)
        return False, (
            f"Source project terlalu besar untuk diupload ({size_mb:.1f}MB). "
            f"Batas saat ini {MAX_CONTENTS_API_ZIP_SIZE // (1024*1024)}MB. "
            f"Coba hapus folder build/ , .gradle/ , atau file besar yang tidak perlu sebelum upload."
        )

    with open(zip_path, "rb") as f:
        content_b64 = base64.b64encode(f.read()).decode()

    file_path = f"build-sources/{build_id}.zip"
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/contents/{file_path}"
    payload = {
        "message": f"chore: upload source untuk build {build_id}",
        "content": content_b64,
        "branch": branch_name,
    }

    last_status, last_text = None, ""
    for attempt in range(3):
        async with httpx.AsyncClient() as client:
            resp = await client.put(url, headers=_headers(), json=payload, timeout=120)
        if resp.status_code in (200, 201):
            return True, file_path
        last_status, last_text = resp.status_code, resp.text[:200]
        if resp.status_code in (404, 409, 422) and attempt < 2:
            await asyncio.sleep(2)
            continue
        break

    return False, f"Gagal upload source: {last_status} {last_text}"


async def delete_branch(branch_name: str) -> None:
    """Hapus branch temporary setelah build selesai (cleanup)."""
    url = f"{GITHUB_API}/repos/{config.GITHUB_REPO}/git/refs/heads/{branch_name}"
    async with httpx.AsyncClient() as client:
        try:
            await client.delete(url, headers=_headers(), timeout=15)
        except httpx.HTTPError:
            pass
