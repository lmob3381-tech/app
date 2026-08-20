#!/usr/bin/env python3
"""
Adds and/or removes <uses-permission> entries in an existing AndroidManifest.xml,
used for the "source-to-apk" (Gradle/Flutter) flow where the user uploads their
own project but wants to customize permissions without editing the manifest by hand.

Usage:
  python3 patch_manifest_permissions.py --manifest path/to/AndroidManifest.xml \
      --add "CAMERA,RECORD_AUDIO" --remove "READ_CONTACTS"
"""
import argparse
import re
import xml.etree.ElementTree as ET

ANDROID_NS = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID_NS)


def normalize(perm: str) -> str:
    perm = perm.strip()
    if not perm:
        return ""
    if "." in perm:
        return perm
    return f"android.permission.{perm.upper()}"


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--add", default="")
    ap.add_argument("--remove", default="")
    args = ap.parse_args()

    add_list = [normalize(p) for p in args.add.split(",") if p.strip()]
    remove_list = [normalize(p) for p in args.remove.split(",") if p.strip()]

    tree = ET.parse(args.manifest)
    root = tree.getroot()

    ns_attr = f"{{{ANDROID_NS}}}name"

    # Remove requested permissions
    if remove_list:
        for elem in list(root.findall("uses-permission")):
            name = elem.get(ns_attr)
            if name in remove_list:
                root.remove(elem)

    # Collect existing permission names to avoid duplicates
    existing = {
        elem.get(ns_attr)
        for elem in root.findall("uses-permission")
    }

    # Add requested permissions
    insert_index = list(root).index(root.findall("application")[0]) if root.findall("application") else len(list(root))
    for perm in add_list:
        if perm in existing:
            continue
        new_elem = ET.Element("uses-permission")
        new_elem.set(ns_attr, perm)
        root.insert(insert_index, new_elem)
        insert_index += 1
        existing.add(perm)

    # Pretty-print for readability (ElementTree doesn't format by default)
    try:
        ET.indent(tree, space="    ")
    except AttributeError:
        pass  # Python < 3.9 fallback: writes without indentation, still valid XML

    tree.write(args.manifest, encoding="utf-8", xml_declaration=True)
    print(f"Patched manifest: {args.manifest}")
    print(f"  added  : {add_list}")
    print(f"  removed: {remove_list}")


if __name__ == "__main__":
    main()
