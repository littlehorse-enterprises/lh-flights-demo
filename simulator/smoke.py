#!/usr/bin/env python3
"""Real end-to-end acceptance check. Run against a fresh stack with RESPONSE_SECONDS=15."""

import argparse, time, uuid
from client import request, wait_for


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--url", default="http://localhost:8081")
    p.add_argument("--timeout", type=int, default=240)
    a = p.parse_args()
    url = a.url
    token = uuid.uuid4().hex[:8]
    request(url, "/flights/LH101-2026-11-05/delay", {"minutes": 0})
    booked = []
    for i in range(5):
        item = request(
            url,
            "/bookings",
            {
                "passengerId": f"p{i+1:03d}",
                "flightNumbers": ["LH101", "LH220" if i < 3 else "LH250"],
                "date": "2026-11-05",
            },
            key=f"smoke-{token}-{i}",
        )
        booked.append(item["tripId"])
    wait_for(
        lambda: all(
            any(
                t["tripId"] == id and t["revision"] == 1 for t in request(url, "/trips")
            )
            for id in booked
        ),
        "booking output in Streams",
    )
    print("PASS booking → LittleHorse output → trip view", flush=True)
    request(url, "/flights/LH101-2026-11-05/delay", {"minutes": 10})
    time.sleep(2)
    assert not any(
        r["tripId"] in booked for r in request(url, "/connections/at-risk")
    ), "10-minute delay must be safe"
    request(url, "/flights/LH101-2026-11-05/delay", {"minutes": 25})

    def pending():
        proposals = {
            o["tripId"]: o
            for o in request(url, "/rebooking-proposals")
            if o["tripId"] in booked
        }
        return (
            proposals
            if all(
                id in proposals and proposals[id]["status"] == "PENDING"
                for id in booked[:3]
            )
            else None
        )

    offers = wait_for(pending, "Streams → Connect → three recovery offers", timeout=120)
    assert not any(
        id in offers for id in booked[3:]
    ), "Safe SEA passengers received offers"
    print(
        "PASS selective connection detection → Connect → recovery workflows", flush=True
    )
    accepted = offers[booked[0]]
    declined = offers[booked[1]]
    expired = offers[booked[2]]
    payload = {"accepted": True, "optionId": accepted["recommendedOptionId"]}
    request(url, f"/rebooking-proposals/{accepted['id']}/response", payload)
    request(url, f"/rebooking-proposals/{accepted['id']}/response", payload)
    request(url, f"/rebooking-proposals/{declined['id']}/response", {"accepted": False})
    wait_for(
        lambda: request(url, f"/rebooking-proposals/{accepted['id']}")["status"]
        == "REBOOKED",
        "confirmed rebooking",
    )
    wait_for(
        lambda: request(url, f"/rebooking-proposals/{declined['id']}")["status"]
        == "DECLINED",
        "declined offer",
    )
    wait_for(
        lambda: request(url, f"/rebooking-proposals/{expired['id']}")["status"]
        == "TIMED_OUT",
        "LittleHorse response timeout",
        a.timeout,
    )
    trip = request(url, f"/trips/{booked[0]}")
    assert trip["tripId"] == booked[0] and trip["revision"] == 2
    assert request(url, f"/trips/{booked[1]}")["revision"] == 1
    print(
        "PASS accept, duplicate response, decline, timeout, and preserved PNR",
        flush=True,
    )
    request(url, "/flights/LH101-2026-11-05/delay", {"minutes": 30})
    time.sleep(2)
    assert (
        len(
            [
                o
                for o in request(url, "/rebooking-proposals")
                if o["tripId"] in booked[:3] and o["revision"] == 1
            ]
        )
        == 3
    )
    request(url, f"/trips/{booked[0]}/cancel", {})
    wait_for(
        lambda: any(
            t["tripId"] == booked[0] and t["status"] == "CANCELLED"
            for t in request(url, "/trips")
        ),
        "cancellation output",
    )
    wait_for(
        lambda: all(
            r["tripId"] != booked[0] for r in request(url, "/connections/at-risk")
        ),
        "cancelled trip index cleanup",
    )
    print("PASS duplicate delay suppression and cancellation cleanup", flush=True)
    print("End-to-end smoke passed.", flush=True)


if __name__ == "__main__":
    main()
