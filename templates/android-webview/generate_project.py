#!/usr/bin/env python3
"""
Generate proyek Android WebView minimal dari parameter CLI.
Dipanggil oleh GitHub Actions workflow web-to-apk.yml.
"""
import argparse
import os
import shutil
import sys

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
SKELETON_DIR = os.path.join(SCRIPT_DIR, "skeleton")


def parse_args():
    p = argparse.ArgumentParser()
    p.add_argument("--url", required=True)
    p.add_argument("--app-name", required=True)
    p.add_argument("--package-name", required=True)
    p.add_argument("--version-name", default="1.0.0")
    p.add_argument("--version-code", default="1")
    p.add_argument("--orientation", default="unspecified")
    p.add_argument("--fullscreen", default="false")
    p.add_argument("--output-dir", required=True)
    return p.parse_args()


def render(template_str: str, ctx: dict) -> str:
    for key, val in ctx.items():
        template_str = template_str.replace("{{" + key + "}}", str(val))
    return template_str


def main():
    args = parse_args()

    if os.path.exists(args.output_dir):
        shutil.rmtree(args.output_dir)
    shutil.copytree(SKELETON_DIR, args.output_dir)

    package_path = args.package_name.replace(".", "/")
    android_orientation = {
        "portrait": "portrait",
        "landscape": "landscape",
        "unspecified": "unspecified",
    }.get(args.orientation, "unspecified")

    ctx = {
        "APP_NAME": args.app_name,
        "PACKAGE_NAME": args.package_name,
        "PACKAGE_PATH": package_path,
        "VERSION_NAME": args.version_name,
        "VERSION_CODE": args.version_code,
        "TARGET_URL": args.url,
        "ORIENTATION": android_orientation,
        "FULLSCREEN": "true" if args.fullscreen == "true" else "false",
    }

    # Render build.gradle & AndroidManifest.xml
    for rel_path in (
        "app/build.gradle",
        "app/src/main/AndroidManifest.xml",
        "app/src/main/res/values/strings.xml",
        "settings.gradle",
    ):
        full_path = os.path.join(args.output_dir, rel_path)
        with open(full_path, "r") as f:
            content = f.read()
        content = render(content, ctx)
        with open(full_path, "w") as f:
            f.write(content)

    # Susun folder Java sesuai package name lalu render MainActivity.java
    java_root = os.path.join(args.output_dir, "app/src/main/java")
    placeholder_dir = os.path.join(java_root, "PACKAGE_PLACEHOLDER")
    target_dir = os.path.join(java_root, package_path)
    os.makedirs(os.path.dirname(target_dir), exist_ok=True)
    shutil.move(placeholder_dir, target_dir)

    main_activity = os.path.join(target_dir, "MainActivity.java")
    with open(main_activity, "r") as f:
        content = f.read()
    content = render(content, ctx)
    with open(main_activity, "w") as f:
        f.write(content)

    print(f"Proyek Android berhasil dibuat di {args.output_dir}")


if __name__ == "__main__":
    main()
