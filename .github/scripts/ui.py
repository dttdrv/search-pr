#!/usr/bin/env python3
"""Tiny UI driver for the smoke test: finds a node in a uiautomator dump and taps it."""
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET


def dump():
    for _ in range(4):
        # Never let a failed dump make the test act on an old screen's hierarchy.
        subprocess.run(["adb", "shell", "rm", "-f", "/sdcard/ui.xml"], capture_output=True)
        result = subprocess.run(["adb", "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
        if result.returncode != 0:
            time.sleep(1)
            continue
        out = subprocess.run(["adb", "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout
        try:
            return ET.fromstring(out)
        except ET.ParseError:
            time.sleep(1)
    return None


def find(root, mode, needle):
    for node in root.iter("node"):
        text = node.get("text", "")
        desc = node.get("content-desc", "")
        hit = {
            "text": text == needle,
            "desc": desc == needle,
            "desc-contains": needle.lower() in desc.lower(),
            "any-contains": needle.lower() in (text + " " + desc).lower(),
        }[mode]
        if hit:
            return node
    return None


def tap(mode, needle):
    root = dump()
    if root is None:
        print(f"ui.py: no dump for {needle}")
        return False
    node = find(root, mode, needle)
    if node is None:
        seen = [
            f"{n.get('class', '').split('.')[-1]}:{n.get('text', '')!r}/{n.get('content-desc', '')!r}"
            for n in root.iter("node")
            if n.get("text") or n.get("content-desc")
        ]
        print(f"ui.py: '{needle}' ({mode}) not found; {len(list(root.iter('node')))} nodes; labelled: {seen[:40]}")
        return False
    x1, y1, x2, y2 = map(int, re.findall(r"\d+", node.get("bounds")))
    subprocess.run(["adb", "shell", "input", "tap", str((x1 + x2) // 2), str((y1 + y2) // 2)])
    print(f"ui.py: tapped '{needle}'")
    return True


def selected(needle):
    root = dump()
    node = find(root, "text", needle) if root is not None else None
    if node is None or node.get("selected") != "true":
        print(f"ui.py: '{needle}' is not selected")
        return False
    print(f"ui.py: '{needle}' is selected")
    return True


if __name__ == "__main__":
    if sys.argv[1] == "selected":
        ok = selected(sys.argv[2])
    elif sys.argv[1] == "exists":
        root = dump()
        ok = root is not None and find(root, "any-contains", sys.argv[2]) is not None
    else:
        ok = tap(sys.argv[1], sys.argv[2])
    sys.exit(0 if ok else 1)
