#!/usr/bin/env python3
"""Generate an offline, reproducible synthetic network; no airline API or scraping needed."""

import datetime as dt
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent
START = dt.date(2026, 11, 5)
flights = []


def flight(number, day, origin, destination, departure, minutes, seats=300):
    depart = dt.datetime.combine(
        START + dt.timedelta(days=day),
        dt.time.fromisoformat(departure),
        dt.timezone.utc,
    )
    arrive = depart + dt.timedelta(minutes=minutes)
    iso = lambda x: x.isoformat().replace("+00:00", "Z")
    flights.append(
        {
            "flight": {
                "flightKey": f"{number}-{depart.date()}",
                "flightNumber": number,
                "date": str(depart.date()),
                "origin": origin,
                "destination": destination,
                "departure": iso(depart),
                "arrival": iso(arrive),
                "boardingCloses": iso(depart - dt.timedelta(minutes=15)),
            },
            "seats": seats,
        }
    )


for day in range(2):
    flight("LH101", day, "RNO", "DEN", "15:15", 105)
    flight("LH220", day, "DEN", "BOS", "18:05", 225)
    flight("LH250", day, "DEN", "SEA", "19:10", 165)
    flight("LH221", day, "DEN", "BOS", "19:30", 225)
    flight("LH310", day, "DEN", "ORD", "18:30", 140)
    flight("LH311", day, "ORD", "BOS", "21:45", 125)
# Two banks each day, 16 spokes and four hubs, with geographically plausible block times.
spokes = [
    "RNO",
    "BOS",
    "SEA",
    "ORD",
    "LAX",
    "SFO",
    "JFK",
    "DFW",
    "MIA",
    "MSP",
    "PHX",
    "IAD",
    "PDX",
    "SAN",
    "LAS",
    "MCO",
]
hubs = {
    "DEN": [
        105,
        225,
        165,
        140,
        145,
        160,
        235,
        115,
        235,
        115,
        110,
        195,
        165,
        145,
        120,
        215,
    ],
    "ATL": [
        250,
        155,
        315,
        125,
        285,
        310,
        135,
        135,
        115,
        155,
        250,
        110,
        310,
        280,
        270,
        90,
    ],
    "SLC": [
        85,
        270,
        130,
        180,
        115,
        120,
        275,
        155,
        285,
        145,
        100,
        245,
        125,
        120,
        100,
        265,
    ],
    "DTW": [
        255,
        115,
        285,
        80,
        280,
        285,
        105,
        175,
        185,
        105,
        250,
        90,
        280,
        270,
        245,
        155,
    ],
}
number = 1000
for hub, durations in hubs.items():
    for spoke, duration in zip(spokes, durations):
        for bank in [12, 18]:
            inbound = number
            outbound = number + 1
            number += 2
            for day in range(2):
                # Arrivals converge at bank :00; departures are 70 minutes later.
                arrival = dt.datetime.combine(
                    START + dt.timedelta(days=day), dt.time(bank), dt.timezone.utc
                )
                dep = arrival - dt.timedelta(minutes=duration)
                flight(
                    f"LH{inbound}", day, spoke, hub, dep.time().isoformat(), duration
                )
                flight(f"LH{outbound}", day, hub, spoke, f"{bank+1:02d}:10", duration)
first = [
    "Ava",
    "Ben",
    "Cara",
    "Diego",
    "Elena",
    "Amara",
    "Noah",
    "Isla",
    "Lucas",
    "Maya",
    "Ethan",
    "Zoe",
    "Liam",
    "Nora",
    "Owen",
    "Aria",
    "Leo",
    "Eva",
    "Finn",
    "Sofia",
]
last = [
    "Chen",
    "Ortiz",
    "Singh",
    "Park",
    "Reed",
    "Bennett",
    "Rivera",
    "Kim",
    "Patel",
    "Morgan",
    "Brooks",
    "Ali",
    "Reyes",
    "Clark",
    "Cruz",
    "Stone",
    "Evans",
    "Hughes",
    "Shah",
    "Wright",
    "Hayes",
    "Ward",
    "Fox",
    "Lane",
    "Cole",
]
preferences = [
    "Prefer a direct flight and get me there tonight.",
    "A connection is okay if I can leave sooner.",
    "Prefer the simplest route; I may be away from my phone.",
    "Arriving tomorrow is okay; minimize connections.",
]
passengers = []
for i in range(500):
    passengers.append(
        {
            "id": f"p{i+1:03d}",
            "name": f"{first[i%20]} {last[(i//20+i)%25]}",
            "preferences": preferences[i % 4],
            "allowOvernight": i % 4 == 3,
        }
    )
for i, name in enumerate(
    ["Ava Chen", "Ben Ortiz", "Cara Singh", "Diego Park", "Elena Reed"]
):
    passengers[i]["name"] = name
(ROOT / "flights.json").write_text(json.dumps(flights, indent=2) + "\n")
(ROOT / "passengers.json").write_text(json.dumps(passengers, indent=2) + "\n")
print(f"Generated {len(flights)} flight instances and {len(passengers)} passengers.")
