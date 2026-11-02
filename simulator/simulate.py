#!/usr/bin/env python3
"""Generate deterministic municipal service activity through the demo webhooks."""

import argparse
import concurrent.futures
import datetime
import json
import math
import random
import sys
import time
import urllib.error
import urllib.parse
import urllib.request


DEFAULT_BASE_URL = "http://localhost:8081"
MUNICIPALITIES = ("MUNI-CENTRAL", "MUNI-EAST", "MUNI-NORTH", "MUNI-SOUTH", "MUNI-WEST")
CENTERS = (
    (39.9526, -75.1652),
    (40.4406, -79.9959),
    (39.1031, -84.5120),
    (38.6270, -90.1994),
    (39.7684, -86.1581),
)
SCENARIOS = (
    {
        "name": "warning",
        "wf-run-id": "demo-overfill-warning",
        "customer-id": "customer-demo-001",
        "truck-id": "truck-001",
        "municipality-code": "MUNI-NORTH",
        "service-stop-id": "stop-demo-warning",
    },
    {
        "name": "tag-purchase",
        "wf-run-id": "demo-overfill-tag-purchase",
        "customer-id": "customer-demo-002",
        "truck-id": "truck-002",
        "municipality-code": "MUNI-EAST",
        "service-stop-id": "stop-demo-tag-purchase",
    },
    {
        "name": "corrective-success",
        "wf-run-id": "demo-corrective-success",
        "customer-id": "customer-demo-003",
        "truck-id": "truck-003",
        "municipality-code": "MUNI-CENTRAL",
        "service-stop-id": "stop-demo-corrective-success",
    },
    {
        "name": "recovery",
        "wf-run-id": "demo-corrective-recovery",
        "customer-id": "customer-demo-004",
        "truck-id": "truck-004",
        "municipality-code": "MUNI-SOUTH",
        "service-stop-id": "stop-demo-corrective-recovery",
    },
    {
        "name": "positive-verification",
        "wf-run-id": "demo-positive-verification",
        "customer-id": "customer-demo-005",
        "truck-id": "truck-005",
        "municipality-code": "MUNI-WEST",
        "service-stop-id": "stop-demo-positive-verification",
    },
    {
        "name": "fee-assessment",
        "wf-run-id": "demo-overfill-fee-assessment",
        "customer-id": "customer-demo-006",
        "truck-id": "truck-006",
        "municipality-code": "MUNI-SOUTH",
        "service-stop-id": "stop-demo-fee-assessment",
    },
)


def utc_timestamp(offset_seconds=0):
    now = datetime.datetime.now(datetime.timezone.utc) + datetime.timedelta(seconds=offset_seconds)
    return now.isoformat(timespec="milliseconds").replace("+00:00", "Z")


def request_json(method, url, payload=None, timeout=5.0):
    data = None if payload is None else json.dumps(payload, separators=(",", ":")).encode("utf-8")
    request = urllib.request.Request(url, data=data, method=method)
    request.add_header("Accept", "application/json")
    if data is not None:
        request.add_header("Content-Type", "application/json")
    try:
        with urllib.request.urlopen(request, timeout=timeout) as response:
            body = response.read()
            return json.loads(body) if body else None
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {url} returned {error.code}: {detail}") from error
    except urllib.error.URLError as error:
        raise RuntimeError(f"{method} {url} failed: {error.reason}") from error


class Simulator:
    def __init__(self, base_url, duration, seed, quiet=False):
        self.base_url = base_url.rstrip("/")
        self.duration = duration
        self.random = random.Random(seed)
        self.quiet = quiet
        self.started = None
        self.sent = 0
        self.customer_call_completed = False
        self.customer_call_started_at = None
        self.recovery_completed = False
        self.recovery_started_at = None
        self.run_token = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%d%H%M%S")

    def log(self, message):
        if not self.quiet:
            elapsed = 0 if self.started is None else int(time.monotonic() - self.started)
            print(f"[{elapsed:03d}s] {message}", flush=True)

    def post(self, topic, payload):
        request_json("POST", f"{self.base_url}/webhooks/{topic}", payload)
        self.sent += 1

    def common(self, scenario):
        return {
            "event-id": f"{scenario['wf-run-id']}-{self.run_token}",
            "customer-id": scenario["customer-id"],
            "truck-id": scenario["truck-id"],
            "municipality-code": scenario["municipality-code"],
            "service-stop-id": f"{scenario['service-stop-id']}-{self.run_token}",
        }

    def overfilled(self, scenario, action, response):
        common = self.common(scenario)
        decision = {
            "warning": "warning",
            "additional-trash": "tag-upsell",
            "assess-fee": "assess-fee",
        }[action]
        payload = {
            key: common[key]
            for key in ("event-id", "customer-id", "truck-id", "municipality-code", "service-stop-id")
        }
        payload.update({
            "address": f"{100 + int(scenario['truck-id'][-3:])} Demo Route",
            "scenario": decision,
            "customer-accepted": response == "accepted",
            "tag-price": 6.0,
            "fee-amount": 25.0,
        })
        self.post("overfilled-cans", payload)
        self.log(f"scenario {scenario['name']}: overfilled can ({response})")

    def no_service(self, scenario, reason):
        payload = self.common(scenario)
        payload.update({
            "address": f"{100 + int(scenario['truck-id'][-3:])} Demo Route",
            "customer-accepted": True,
            "expect-timeout": scenario["name"] == "recovery",
            "price": 15.0,
        })
        self.post("no-service-events", payload)
        self.log(f"scenario {scenario['name']}: corrective pickup accepted")

    def service_complete(self, scenario, recovered=False):
        common = self.common(scenario)
        payload = {
            "eventId": common["event-id"],
            "customerId": common["customer-id"],
            "truckId": common["truck-id"],
            "serviceStopId": common["service-stop-id"],
            "status": "completed",
            "completionSource": "pickup-detection",
            "recovered": recovered,
            "completedAt": utc_timestamp(),
        }
        self.post("service-exceptions", payload)
        detail = "replacement service verified" if recovered else "service verified"
        self.log(f"scenario {scenario['name']}: {detail}")

    def contact_event_id(self, scenario):
        suffix = "tag-offer" if scenario["name"] == "tag-purchase" else "offer"
        return f"{self.common(scenario)['event-id']}-{suffix}"

    def customer_response(self, scenario, channel, accepted):
        payload = {
            "eventId": self.contact_event_id(scenario),
            "customerId": scenario["customer-id"],
            "channel": channel,
            "accepted": accepted,
            "choice": "accepted" if accepted else "declined",
            "respondedAt": utc_timestamp(),
        }
        self.post("customer-responses", payload)
        self.log(f"scenario {scenario['name']}: customer {payload['choice']} via {channel}")

    def location(self, truck_number, second):
        municipality_index = (truck_number - 1) % len(MUNICIPALITIES)
        lat, lon = CENTERS[municipality_index]
        angle = truck_number * 0.37 + second * 0.025
        route_progress = ((second * 7 + truck_number * 13) % 1000) / 1000.0
        return {
            "event-id": f"location-{second:04d}-{truck_number:03d}",
            "truck-id": f"truck-{truck_number:03d}",
            "municipality-code": MUNICIPALITIES[municipality_index],
            "occurred-at": utc_timestamp(),
            "latitude": round(lat + 0.035 * math.sin(angle), 6),
            "longitude": round(lon + 0.045 * math.cos(angle), 6),
            "heading-degrees": (truck_number * 29 + second * 11) % 360,
            "speed-mph": round(12.0 + 18.0 * abs(math.sin(angle / 2)), 1),
            "route-progress": round(route_progress, 3),
        }

    def ordinary_pickup(self, second, sequence):
        truck_number = ((second * 3 + sequence * 31) % 100) + 1
        customer_number = 1000 + second * 3 + sequence
        stop_id = f"stop-routine-{second:04d}-{sequence}"
        return {
            "event-id": f"pickup-routine-{self.run_token}-{second:04d}-{sequence}",
            "service-stop-id": f"{stop_id}-{self.run_token}",
            "customer-id": f"customer-{customer_number:06d}",
            "truck-id": f"truck-{truck_number:03d}",
            "municipality-code": MUNICIPALITIES[(truck_number - 1) % len(MUNICIPALITIES)],
        }

    def complete_recovery_task(self):
        tasks = request_json("GET", f"{self.base_url}/api/user-tasks") or []
        scenario = SCENARIOS[3]
        for task in tasks:
            task_text = json.dumps(task, sort_keys=True)
            belongs_to_recovery = all(
                identifier in task_text
                for identifier in (scenario["customer-id"], self.run_token)
            )
            if not belongs_to_recovery:
                continue
            if task.get("status") not in ("ASSIGNED", "UNASSIGNED"):
                continue
            run_id = urllib.parse.quote(task["wfRunId"], safe="")
            guid = urllib.parse.quote(task["userTaskGuid"], safe="")
            if task.get("status") == "UNASSIGNED":
                request_json(
                    "POST",
                    f"{self.base_url}/api/user-tasks/{run_id}/{guid}/claim",
                    {"userId": "simulation-operator"},
                )
            request_json(
                "POST",
                f"{self.base_url}/api/user-tasks/{run_id}/{guid}/complete",
                {
                    "userId": "simulation-operator",
                    "approved": True,
                    "note": "Replacement route approved after automatic service timeout.",
                },
            )
            self.recovery_completed = True
            self.log("scenario recovery: operator approved replacement service")
            self.service_complete(scenario, recovered=True)
            return

    def complete_customer_call_task(self):
        tasks = request_json("GET", f"{self.base_url}/api/user-tasks") or []
        scenario = SCENARIOS[2]
        for task in tasks:
            if task.get("formName") != "customer-call":
                continue
            task_text = json.dumps(task, sort_keys=True)
            belongs_to_success = all(
                identifier in task_text
                for identifier in (scenario["customer-id"], self.run_token)
            )
            if not belongs_to_success:
                continue
            if task.get("status") not in ("ASSIGNED", "UNASSIGNED"):
                continue
            run_id = urllib.parse.quote(task["wfRunId"], safe="")
            guid = urllib.parse.quote(task["userTaskGuid"], safe="")
            if task.get("status") == "UNASSIGNED":
                request_json(
                    "POST",
                    f"{self.base_url}/api/user-tasks/{run_id}/{guid}/claim",
                    {"userId": "customer-service-agent"},
                )
            request_json(
                "POST",
                f"{self.base_url}/api/user-tasks/{run_id}/{guid}/complete",
                {
                    "userId": "customer-service-agent",
                    "accepted": True,
                    "note": "Customer accepted the corrective pickup during the call.",
                },
            )
            self.customer_call_completed = True
            self.log("scenario corrective-success: customer service agent recorded an accepted call")
            return

    def run(self):
        request_json("GET", f"{self.base_url}/api/outcomes")
        request_json("POST", f"{self.base_url}/api/reset")
        self.post("simulation-started", {"started-at": utc_timestamp()})
        self.started = time.monotonic()
        self.log("starting 100 deterministic trucks")
        warning_at = max(2, round(self.duration * 0.08))
        fee_at = max(3, round(self.duration * 0.12))
        tags_at = max(4, round(self.duration * 0.18))
        success_at = max(6, round(self.duration * 0.30))
        recovery_at = max(10, round(self.duration * 0.45))
        positive_at = max(14, round(self.duration * 0.72))
        scenario_offsets = {
            warning_at: ("overfilled", SCENARIOS[0], "warning", "not-required"),
            fee_at: ("overfilled", SCENARIOS[5], "assess-fee", "not-required"),
            tags_at: ("overfilled", SCENARIOS[1], "additional-trash", "accepted"),
            tags_at + 4: ("response", SCENARIOS[1], "TEXT", True),
            tags_at + 12: ("complete", SCENARIOS[1], False),
            success_at: ("no-service", SCENARIOS[2], "missed-scheduled-service"),
            success_at + 8: ("complete", SCENARIOS[2], False),
            recovery_at: ("no-service", SCENARIOS[3], "missed-corrective-service"),
            recovery_at + 4: ("response", SCENARIOS[3], "TEXT", True),
            positive_at: ("no-service", SCENARIOS[4], "container-not-present"),
            positive_at + 4: ("response", SCENARIOS[4], "PUSH", True),
            positive_at + 8: ("complete", SCENARIOS[4], False),
        }
        next_second = 0
        last_customer_call_poll = 0.0
        last_recovery_poll = 0.0
        failures = []
        with concurrent.futures.ThreadPoolExecutor(max_workers=16) as executor:
            while True:
                elapsed = time.monotonic() - self.started
                if elapsed >= self.duration:
                    break
                second = int(elapsed)
                if second >= next_second:
                    next_second = second + 1
                    action = scenario_offsets.pop(second, None)
                    if action:
                        if action[0] == "overfilled":
                            self.overfilled(action[1], action[2], action[3])
                        elif action[0] == "no-service":
                            self.no_service(action[1], action[2])
                            if action[1]["name"] == "corrective-success":
                                self.customer_call_started_at = time.monotonic()
                            elif action[1]["name"] == "recovery":
                                self.recovery_started_at = time.monotonic()
                        elif action[0] == "response":
                            self.customer_response(action[1], action[2], action[3])
                        else:
                            self.service_complete(action[1], action[2])

                    batch = []
                    first_truck = (second * 25) % 100 + 1
                    for offset in range(25):
                        truck_number = ((first_truck + offset - 1) % 100) + 1
                        batch.append(executor.submit(self.post, "truck-locations", self.location(truck_number, second)))
                    for sequence in range(3):
                        batch.append(executor.submit(self.post, "pickups", self.ordinary_pickup(second, sequence)))
                    for future in concurrent.futures.as_completed(batch):
                        try:
                            future.result()
                        except Exception as error:
                            failures.append(str(error))

                if self.customer_call_started_at and not self.customer_call_completed:
                    if elapsed - last_customer_call_poll >= 1.0:
                        last_customer_call_poll = elapsed
                        try:
                            self.complete_customer_call_task()
                        except RuntimeError:
                            pass

                if self.recovery_started_at and not self.recovery_completed:
                    if elapsed - last_recovery_poll >= 1.0 and time.monotonic() - self.recovery_started_at >= 5.0:
                        last_recovery_poll = elapsed
                        try:
                            self.complete_recovery_task()
                        except RuntimeError:
                            pass
                time.sleep(0.05)

        self.post("simulation-stopped", {"stopped-at": utc_timestamp()})
        if failures:
            unique = list(dict.fromkeys(failures))
            raise RuntimeError(f"{len(failures)} request(s) failed; first errors: {'; '.join(unique[:3])}")
        if not self.recovery_completed:
            raise RuntimeError("scenario recovery user task did not appear before the simulation ended")
        if not self.customer_call_completed:
            raise RuntimeError("scenario corrective-success customer call did not complete before the simulation ended")
        self.log(f"complete: {self.sent} webhook events sent")


def parse_args(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--duration", type=int, default=300, help="simulation seconds (default: 300; minimum: 45)")
    parser.add_argument("--base-url", default=DEFAULT_BASE_URL, help=f"backend URL (default: {DEFAULT_BASE_URL})")
    parser.add_argument("--seed", type=int, default=20260815, help="ordinary-event seed")
    parser.add_argument("--quiet", action="store_true", help="only print errors")
    args = parser.parse_args(argv)
    if args.duration < 45:
        parser.error("--duration must be at least 45 seconds so timeout recovery can complete")
    return args


def main(argv=None):
    args = parse_args(argv)
    try:
        Simulator(args.base_url, args.duration, args.seed, args.quiet).run()
        return 0
    except (RuntimeError, KeyboardInterrupt) as error:
        print(f"simulation failed: {error}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
