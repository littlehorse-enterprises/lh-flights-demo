"""Standard-library HTTP helpers shared by simulator, seed, and smoke scripts."""

import json
import time
import urllib.error
import urllib.request


def request(base, path, payload=None, key=None):
    headers = {"Content-Type": "application/json"}
    if key:
        headers["Idempotency-Key"] = key
    req = urllib.request.Request(
        base.rstrip("/") + "/api" + path,
        data=None if payload is None else json.dumps(payload).encode(),
        headers=headers,
    )
    try:
        with urllib.request.urlopen(req, timeout=30) as response:
            raw = response.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raise RuntimeError(
            f"{req.method} {path}: HTTP {error.code}: {error.read().decode()}"
        ) from error


def wait_for(fn, description, timeout=90):
    end = time.monotonic() + timeout
    last = None
    while time.monotonic() < end:
        try:
            result = fn()
            if result:
                return result
        except (RuntimeError, urllib.error.URLError) as error:
            last = error
        time.sleep(0.5)
    raise RuntimeError(f"Timed out waiting for {description}; last error: {last}")
