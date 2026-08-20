#!/usr/bin/env python3
"""
Injects app-specific config into the WebView Android template:
- app name (strings.xml)
- package id (build.gradle applicationId + folder move + MainActivity package decl)
- target URL + JS toggle (MainActivity.kt)
- versionName / versionCode (build.gradle)

Usage:
  python3 gen_webview_config.py --project-dir work/app-project \
      --app-name "My App" --package com.example.myapp \
      --url "https://example.com" --allow-js true \
      --version-name 1.0.0 --version-code 1
"""
import argparse
import os
import re
import shutil


def update_strings_xml(project_dir, app_name):
    path = os.path.join(project_dir, "app/src/main/res/values/strings.xml")
    content = (
        '<?xml version="1.0" encoding="utf-8"?>\n'
        "<resources>\n"
        f'    <string name="app_name">{escape_xml(app_name)}</string>\n'
        "</resources>\n"
    )
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def escape_xml(s):
    return (
        s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace('"', "&quot;")
        .replace("'", "&apos;")
    )


def update_build_gradle(project_dir, package_id, version_name, version_code):
    path = os.path.join(project_dir, "app/build.gradle")
    with open(path, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(r'applicationId\s+"[^"]*"', f'applicationId "{package_id}"', content)
    content = re.sub(r'namespace\s+"[^"]*"', f'namespace "{package_id}"', content)
    content = re.sub(r"versionName\s+\"[^\"]*\"", f'versionName "{version_name}"', content)
    content = re.sub(r"versionCode\s+\d+", f"versionCode {version_code}", content)

    with open(path, "w", encoding="utf-8") as f:
        f.write(content)


def move_kotlin_package(project_dir, package_id):
    """Move MainActivity.kt into the correct package folder and rewrite its package declaration."""
    src_root = os.path.join(project_dir, "app/src/main/java")
    # Find existing MainActivity.kt anywhere under src_root
    main_activity_path = None
    for root, _, files in os.walk(src_root):
        for f in files:
            if f == "MainActivity.kt":
                main_activity_path = os.path.join(root, f)
                break
    if not main_activity_path:
        raise FileNotFoundError("MainActivity.kt not found in template")

    with open(main_activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(r"^package .*$", f"package {package_id}", content, count=1, flags=re.MULTILINE)

    new_dir = os.path.join(src_root, *package_id.split("."))
    os.makedirs(new_dir, exist_ok=True)
    new_path = os.path.join(new_dir, "MainActivity.kt")

    # Remove old empty package tree, write new file
    with open(new_path, "w", encoding="utf-8") as f:
        f.write(content)

    if os.path.abspath(new_path) != os.path.abspath(main_activity_path):
        os.remove(main_activity_path)
        # Clean up now-empty directories
        d = os.path.dirname(main_activity_path)
        while d != src_root and os.path.isdir(d) and not os.listdir(d):
            os.rmdir(d)
            d = os.path.dirname(d)

    return new_path


def update_main_activity_config(main_activity_path, url, allow_js):
    with open(main_activity_path, "r", encoding="utf-8") as f:
        content = f.read()

    content = re.sub(
        r'const val TARGET_URL = ".*"',
        f'const val TARGET_URL = "{url}"',
        content,
    )
    js_bool = "true" if str(allow_js).lower() in ("1", "true", "yes") else "false"
    content = re.sub(
        r"const val JS_ENABLED = (true|false)",
        f"const val JS_ENABLED = {js_bool}",
        content,
    )

    with open(main_activity_path, "w", encoding="utf-8") as f:
        f.write(content)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--project-dir", required=True)
    ap.add_argument("--app-name", required=True)
    ap.add_argument("--package", required=True)
    ap.add_argument("--url", required=True)
    ap.add_argument("--allow-js", default="true")
    ap.add_argument("--version-name", default="1.0.0")
    ap.add_argument("--version-code", default="1")
    args = ap.parse_args()

    update_strings_xml(args.project_dir, args.app_name)
    update_build_gradle(args.project_dir, args.package, args.version_name, args.version_code)
    main_activity_path = move_kotlin_package(args.project_dir, args.package)
    update_main_activity_config(main_activity_path, args.url, args.allow_js)

    print("WebView project configured successfully:")
    print(f"  app-name: {args.app_name}")
    print(f"  package : {args.package}")
    print(f"  url     : {args.url}")
    print(f"  js      : {args.allow_js}")


if __name__ == "__main__":
    main()
