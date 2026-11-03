#!/usr/bin/env python3
"""Steady randomized booking traffic. Flight delays are controlled from the UI."""

import argparse, datetime as dt, random, time, uuid
from client import request, wait_for


def routes(flights):
    result = []
    # Published live status affects whether a new connection is feasible.
    for first in flights:
        if first["seats"] < 1:
            continue
        f = first["flight"]
        arrival = dt.datetime.fromisoformat(
            f["arrival"].replace("Z", "+00:00")
        ) + dt.timedelta(minutes=first["delayMinutes"])
        for second in flights:
            g = second["flight"]
            if (
                second["seats"] < 1
                or g["date"] != f["date"]
                or f["destination"] != g["origin"]
                or f["origin"] == g["destination"]
            ):
                continue
            cutoff = dt.datetime.fromisoformat(
                g["boardingCloses"].replace("Z", "+00:00")
            ) + dt.timedelta(minutes=second["delayMinutes"])
            gap = (cutoff - arrival).total_seconds() / 60
            if 35 <= gap <= 240:
                result.append((f, g))
    return result


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--url", default="http://localhost:8081")
    p.add_argument("--interval", type=float, default=1.5)
    p.add_argument("--count", type=int, default=0, help="0 means until Ctrl-C")
    p.add_argument("--seed", type=int)
    p.add_argument("--hub", help="Optional connection airport, e.g. DEN")
    a = p.parse_args()
    if a.interval < 0:
        p.error("--interval must be nonnegative")
    rng = random.Random(a.seed)
    passengers = wait_for(lambda: request(a.url, "/passengers"), "seeded passengers")
    candidates = routes(wait_for(lambda: request(a.url, "/flights"), "seeded flights"))
    if a.hub:
        candidates = [r for r in candidates if r[0]["destination"] == a.hub.upper()]
    if not candidates:
        p.error("No connecting itineraries available. Run ./seed.sh first.")
    # Balance inbound flights, rather than overweighting flights with many onward options.
    by_inbound = {}
    for route in candidates:
        by_inbound.setdefault(route[0]["flightKey"], []).append(route)
    print(
        f"Booking from {len(passengers)} passengers, {len(candidates)} connecting itineraries. Inject delays at http://localhost:5173/backoffice",
        flush=True,
    )
    try:
        i = 0
        while a.count == 0 or i < a.count:
            if i and i % 30 == 0:
                refreshed = routes(request(a.url, "/flights"))
                if a.hub:
                    refreshed = [
                        r for r in refreshed if r[0]["destination"] == a.hub.upper()
                    ]
                if refreshed:
                    by_inbound = {}
                    for route in refreshed:
                        by_inbound.setdefault(route[0]["flightKey"], []).append(route)
            first, second = rng.choice(by_inbound[rng.choice(list(by_inbound))])
            person = rng.choice(passengers)
            result = request(
                a.url,
                "/bookings",
                {
                    "passengerId": person["id"],
                    "flightNumbers": [first["flightNumber"], second["flightNumber"]],
                    "date": first["date"],
                },
                key=uuid.uuid4().hex[:12],
            )
            print(
                f"{result['tripId'].upper()}  {person['name']:<22} {first['origin']} → {first['destination']} → {second['destination']}  {first['flightNumber']} / {second['flightNumber']}",
                flush=True,
            )
            i += 1
            time.sleep(a.interval)
    except KeyboardInterrupt:
        print("\nBooking traffic stopped.")


if __name__ == "__main__":
    main()
