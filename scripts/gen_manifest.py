#!/usr/bin/env python3
"""
Generate AndroidManifest.xml for the WebView wrapper app, with a custom
list of Android permissions.

Usage:
  python3 gen_manifest.py --package com.example.app --permissions "INTERNET,CAMERA" --out AndroidManifest.xml
"""
import argparse

MANIFEST_TEMPLATE = """<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="{package}">

{permissions_block}

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:label="@string/app_name"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.WebViewApp">
        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:configChanges="orientation|screenSize|keyboardHidden">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
"""

# Known safe defaults; anything not in this map is still allowed through
# as android.permission.<NAME> so users can add custom/OEM permissions too.
KNOWN_PERMISSIONS = {
    "INTERNET",
    "ACCESS_NETWORK_STATE",
    "ACCESS_WIFI_STATE",
    "CAMERA",
    "RECORD_AUDIO",
    "ACCESS_FINE_LOCATION",
    "ACCESS_COARSE_LOCATION",
    "READ_EXTERNAL_STORAGE",
    "WRITE_EXTERNAL_STORAGE",
    "READ_MEDIA_IMAGES",
    "READ_MEDIA_VIDEO",
    "READ_MEDIA_AUDIO",
    "POST_NOTIFICATIONS",
    "BLUETOOTH",
    "BLUETOOTH_ADMIN",
    "BLUETOOTH_CONNECT",
    "BLUETOOTH_SCAN",
    "VIBRATE",
    "WAKE_LOCK",
    "READ_CONTACTS",
    "WRITE_CONTACTS",
    "CALL_PHONE",
    "SEND_SMS",
    "RECEIVE_SMS",
    "READ_SMS",
    "READ_PHONE_STATE",
    "FOREGROUND_SERVICE",
}


def build_permissions_block(permissions_csv: str) -> str:
    if not permissions_csv.strip():
        perms = ["INTERNET"]
    else:
        perms = [p.strip().upper() for p in permissions_csv.split(",") if p.strip()]
        if "INTERNET" not in perms:
            perms.insert(0, "INTERNET")

    lines = []
    for p in perms:
        # Allow fully-qualified custom permissions too, e.g. "com.google.android.gms.permission.AD_ID"
        if "." in p:
            lines.append(f'    <uses-permission android:name="{p}" />')
        else:
            lines.append(f'    <uses-permission android:name="android.permission.{p}" />')
    return "\n".join(lines)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--package", required=True)
    ap.add_argument("--permissions", default="INTERNET")
    ap.add_argument("--out", required=True)
    args = ap.parse_args()

    perms_block = build_permissions_block(args.permissions)
    content = MANIFEST_TEMPLATE.format(package=args.package, permissions_block=perms_block)

    with open(args.out, "w", encoding="utf-8") as f:
        f.write(content)

    print(f"Manifest written to {args.out}")
    print(perms_block)


if __name__ == "__main__":
    main()
