#!/usr/bin/env python3
"""Fail when the JaCoCo reactor aggregate falls below the required line coverage."""

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


report = Path(sys.argv[1])
minimum = float(sys.argv[2])
if not report.is_file():
    raise SystemExit(f"JaCoCo aggregate report not found: {report}")

root = ET.parse(report).getroot()
counter = next((item for item in root.findall("counter") if item.get("type") == "LINE"), None)
if counter is None:
    raise SystemExit(f"JaCoCo aggregate report has no LINE counter: {report}")

covered = int(counter.attrib["covered"])
missed = int(counter.attrib["missed"])
total = covered + missed
ratio = covered / total if total else 1.0
print(f"Reactor aggregate line coverage: {ratio:.1%} ({covered}/{total}); required: {minimum:.0%}")
if ratio < minimum:
    raise SystemExit(1)
