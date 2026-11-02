#!/usr/bin/env python3
"""Run the accelerated simulator and validate connectors and business outcomes."""

import argparse
import json
import subprocess
import sys
import time
import urllib.error
import urllib.request


CONNECTORS = ("pickups", "overfilled-cans", "no-service-events", "service-exceptions", "customer-responses")
SCENARIO_CUSTOMERS = {f"customer-demo-{number:03d}" for number in range(1, 7)}
RESPONDED_CUSTOMERS = {"customer-demo-002", "customer-demo-003", "customer-demo-004", "customer-demo-005"}


def get_json(url, timeout=5.0):
    request = urllib.request.Request(url, headers={"Accept": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            return json.loads(response.read())
    except (urllib.error.HTTPError, urllib.error.URLError, json.JSONDecodeError) as error:
        raise RuntimeError(f"GET {url} failed: {error}") from error


def validate_connectors(connect_url):
    problems = []
    for name in CONNECTORS:
        status = get_json(f"{connect_url.rstrip('/')}/connectors/{name}/status")
        connector_state = status.get("connector", {}).get("state")
        task_states = [task.get("state") for task in status.get("tasks", [])]
        if connector_state != "RUNNING" or not task_states or any(state != "RUNNING" for state in task_states):
            problems.append(f"{name}: connector={connector_state}, tasks={task_states}")
    if problems:
        raise RuntimeError("connectors are not healthy: " + "; ".join(problems))


def validate_outcomes(base_url):
    deadline = time.monotonic() + 15
    outcomes = None
    activity = []
    while time.monotonic() < deadline:
        outcomes = get_json(f"{base_url.rstrip('/')}/api/outcomes")
        activity = get_json(f"{base_url.rstrip('/')}/api/activity")
        if outcomes.get("failedServicesRecovered", 0) >= 1:
            break
        time.sleep(1)

    minimums = {
        "trucksSimulated": 100,
        "serviceEventsProcessed": 100,
        "exceptionsDetected": 6,
        "interactionsWithoutPhoneCall": 4,
        "correctivePickupsSold": 3,
        "additionalTrashPurchases": 1,
        "incrementalRevenue": 76,
        "failedServicesRecovered": 1,
        "ledgerEntries": 5,
    }
    failures = [
        f"{field}={outcomes.get(field, 0)} (expected >= {minimum})"
        for field, minimum in minimums.items()
        if outcomes.get(field, 0) < minimum
    ]
    represented = {item.get("customerId") for item in activity}
    missing = sorted(SCENARIO_CUSTOMERS - represented)
    if missing:
        failures.append("activity missing deterministic customers: " + ", ".join(missing))
    responded = {item.get("customerId") for item in activity if item.get("type") == "customer-response"}
    missing_responses = sorted(RESPONDED_CUSTOMERS - responded)
    if missing_responses:
        failures.append("customer responses missing from activity: " + ", ".join(missing_responses))
    if not any(
        item.get("customerId") == "customer-demo-002"
        and item.get("type") == "dispatch"
        and "next-day" in item.get("detail", "")
        for item in activity
    ):
        failures.append("tag purchase did not produce a next-day dispatch")
    if not any(
        item.get("customerId") == "customer-demo-006"
        and item.get("type") == "revenue"
        and "overfill-fee" in item.get("detail", "")
        for item in activity
    ):
        failures.append("fee assessment did not produce an invoice")
    if failures:
        raise RuntimeError("outcome validation failed: " + "; ".join(failures))
    return outcomes


def parse_args():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=int, default=75, help="accelerated run seconds (default: 75)")
    parser.add_argument("--base-url", default="http://localhost:8081")
    parser.add_argument("--connect-url", default="http://localhost:8083")
    return parser.parse_args()


def main():
    args = parse_args()
    try:
        get_json(f"{args.base_url.rstrip('/')}/api/outcomes")
        validate_connectors(args.connect_url)
        subprocess.run(
            [sys.executable, __file__.replace("smoke.py", "simulate.py"), "--duration", str(args.duration), "--base-url", args.base_url],
            check=True,
        )
        outcomes = validate_outcomes(args.base_url)
        print("smoke passed: " + json.dumps(outcomes, sort_keys=True))
        return 0
    except (RuntimeError, subprocess.CalledProcessError) as error:
        print(f"smoke failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
