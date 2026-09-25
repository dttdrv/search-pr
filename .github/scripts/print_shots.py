#!/usr/bin/env python3
"""Prints downscaled smoke-test screenshots as base64 so they can be read from the job log."""
import base64
import io
import pathlib
import sys

from PIL import Image

width = int(sys.argv[1]) if len(sys.argv) > 1 else 360
for path in sorted(pathlib.Path("shots").glob("*.png")):
    try:
        img = Image.open(path).convert("RGB")
    except Exception as e:  # noqa: BLE001
        print(f"SHOT-SKIP {path.name} {e}")
        continue
    h = int(img.height * width / img.width)
    img = img.resize((width, h), Image.LANCZOS)
    buf = io.BytesIO()
    img.save(buf, "JPEG", quality=70, optimize=True)
    data = base64.b64encode(buf.getvalue()).decode()
    print(f"SHOT-BEGIN {path.stem}")
    for i in range(0, len(data), 200):
        print(data[i:i + 200])
    print(f"SHOT-END {path.stem}")
