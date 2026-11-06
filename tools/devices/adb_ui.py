#!/usr/bin/env python3
"""Tiny adb UI driver for capturing review screenshots: tap a node by its text or content description.

    adb_ui.py tap "Arrived"          # first node whose text/desc contains the string
    adb_ui.py shot path.png
"""
import re, subprocess, sys, time, xml.etree.ElementTree as ET

ADB = "adb"

def dump():
    subprocess.run([ADB, "shell", "uiautomator", "dump", "/sdcard/ui.xml"], capture_output=True)
    return ET.fromstring(subprocess.run([ADB, "exec-out", "cat", "/sdcard/ui.xml"], capture_output=True).stdout)

def find(label, exact=False):
    for n in dump().iter("node"):
        for attr in ("text", "content-desc"):
            v = n.get(attr) or ""
            if (v == label) if exact else (label in v):
                x1, y1, x2, y2 = map(int, re.findall(r"\d+", n.get("bounds")))
                return (x1 + x2) // 2, (y1 + y2) // 2
    return None

def tap(label, exact=False):
    for _ in range(10):
        p = find(label, exact)
        if p:
            subprocess.run([ADB, "shell", "input", "tap", str(p[0]), str(p[1])])
            time.sleep(1.2)
            return
        time.sleep(0.8)
    sys.exit(f"not found: {label}")

def shot(path):
    time.sleep(1.0)
    with open(path, "wb") as f:
        f.write(subprocess.run([ADB, "exec-out", "screencap", "-p"], capture_output=True).stdout)

if __name__ == "__main__":
    cmd, arg = sys.argv[1], sys.argv[2]
    {"tap": tap, "tapx": lambda a: tap(a, exact=True), "shot": shot}[cmd](arg)
