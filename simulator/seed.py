#!/usr/bin/env python3
import argparse, json
from pathlib import Path
from client import request

p = argparse.ArgumentParser(
    description="Load repeatable airline schedules and passengers."
)
p.add_argument("--url", default="http://localhost:8081")
a = p.parse_args()
root = Path(__file__).resolve().parents[1] / "fixtures"
for kind in ["passengers", "flights"]:
    data = json.loads((root / f"{kind}.json").read_text())
    request(a.url, f"/{kind}", data)
    print(f"Loaded {len(data)} {kind}")
