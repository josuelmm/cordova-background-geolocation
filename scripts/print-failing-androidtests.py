#!/usr/bin/env python3
"""v5.0 — print every failing instrumented test inline in the CI log.

Without this the only way to know WHICH androidTest failed is downloading the
uploaded artifact, and Gradle's console output only says "There were failing
tests. See the report at: file:///...". Parses the JUnit XML that
connectedDebugAndroidTest writes and prints class, test, message and the first
stack frame for each failure/error.

Usage: python3 scripts/print-failing-androidtests.py [root ...]
Exit code is always 0: this is a reporting aid, not a gate.
"""
import glob
import os
import sys
import xml.etree.ElementTree as ET

PATTERNS = [
    "**/build/outputs/androidTest-results/**/*.xml",
    "**/build/outputs/androidTest-results/*.xml",
]


def collect(roots):
    files = []
    for root in roots:
        for pat in PATTERNS:
            files.extend(glob.glob(os.path.join(root, pat), recursive=True))
    return sorted(set(files))


def main():
    roots = sys.argv[1:] or ["android"]
    files = collect(roots)
    if not files:
        print("No androidTest-results XML found under: " + ", ".join(roots))
        return 0

    total = 0
    for path in files:
        try:
            root = ET.parse(path).getroot()
        except Exception as exc:  # malformed / truncated on a crashed run
            print("[unparseable] %s: %s" % (path, exc))
            continue
        for tc in root.iter("testcase"):
            for tag in ("failure", "error"):
                for node in tc.findall(tag):
                    total += 1
                    print("\n=== %s.%s [%s] ===" % (tc.get("classname"), tc.get("name"), tag))
                    msg = (node.get("message") or "").strip()
                    if msg:
                        print(msg[:500])
                    body = (node.text or "").strip().splitlines()
                    for line in body[:6]:
                        print("    " + line[:300])

    print("\n%d failing instrumented test(s) across %d result file(s)." % (total, len(files)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
