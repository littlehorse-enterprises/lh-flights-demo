# Connection Rescue

A small airline operations demo for Current 2026. Kafka Streams finds broken connections; one Kafka Connect sink starts a LittleHorse recovery workflow for each affected booking. A passenger sees an itinerary recommendation, accepts or declines it, and the workflow completes the booking change or times out.

The airline data and notifications are synthetic. Kafka, Streams, Connect, PostgreSQL, LittleHorse workflows, correlated events, and configured model calls are real. Schedule times use November 5–6, 2026 UTC. Response deadlines use the actual clock.

## Run

Docker with Compose v2 is the only startup prerequisite. Allow about 8 GB of Docker memory; local models need additional memory and disk. First startup downloads images and builds the applications and connector.

```bash
./setup.sh
```

Choose rules, OpenAI, Anthropic, or a small/4B Qwen model through Ollama. Non-interactive startup defaults to rules. The choice is saved in `.env.local`; credentials live in ignored `.agent.env`. Remove these two files and run setup again to change provider. Model names are editable during setup. Only the selected provider is started. A failed, timed-out or invalid model response falls back to the rules worker.

Open http://localhost:5173/backoffice and start booking traffic:

```bash
python3 simulator/simulate.py
```

The simulator randomly chooses from 500 passengers and connecting itineraries across 524 flight instances. It only submits bookings. It never injects delays. Use `--hub DEN`, `--interval 0.5`, `--count 100`, or `--seed 42` to concentrate or repeat traffic.

In the back office, choose a flight with booked passengers, set its total delay and publish. A 120-minute delay usually produces several affected bookings. Open a pending passenger card, review the explanation and alternatives, then confirm or decline. Open another passenger card and leave it unanswered to show the three-minute LittleHorse timeout. Safe connections remain visible under uninterrupted journeys. Workflow links open the actual LittleHorse Dashboard at http://localhost:3000.

For the featured RNO–DEN example, book LH101 with LH220 to BOS or LH250 to SEA using the API. LH101 delayed by 10 minutes keeps both feasible; 25 minutes breaks BOS while SEA remains feasible. The smoke script exercises this exact distinction.

## How it works

One Quarkus JVM hosts the airline API, normal task workers and vanilla Kafka Streams. PostgreSQL owns passengers, schedules, seats, PNRs and offers. Streams owns the queryable flight, trip and connection-risk views; there is no database-backed Kafka projection. There are seven containers in rules mode: Kafka, LittleHorse, Dashboard, Connect, PostgreSQL, backend and frontend. A configured model adds the agent connector, and Ollama adds one more.

`booking` takes `passengerId`, a native array `flightNumbers`, `date` and the generated `tripId`. Initial booking and `reaccommodate-customer` both call the `reserve-itinerary` child workflow and wait for its result. That workflow emits a typed `trip-booked` event after a successful reservation. Rebooking preserves the PNR. Cancellation emits another booking fact with a new business revision.

Streams decodes native Protobuf output records, converts typed booking events into plain JSON on `trip-bookings`, maintains a partitioned trip store and a flight-to-trip secondary index, and reevaluates affected trips after a booking or status change. `flight-bookings-internal`, `flight-status-internal` and `affected-trips` each have three partitions, matching `trip-bookings`. A compacted internal `flight-status-view` copy feeds the global store: Kafka Streams does not allow a normal source and global source for the same topic in one topology. Affected records carry the triggering observation, and deferred evaluations retry after missing global data catches up.

Only affected passenger/trip pairs go to `broken-connections`. Their `wfRunId` header is `recover-{tripId}-v{bookingRevision}`. Repeated risk records are allowed; Kafka Connect's `RunWf` call uses the same ID and tolerates `ALREADY_EXISTS`. No Streams duplicate-suppression store is needed. A changed booking revision can start a new recovery workflow.

The workflow looks up current booking details and feasible itineraries, obtains a recommendation, validates it, stores an offer, and waits for a typed correlated passenger event. The HTTP response endpoint calls LittleHorse directly. Acceptance is a decision, not a confirmed booking: the child reservation workflow revalidates current inventory and flight delays and commits the seat/itinerary changes in one PostgreSQL transaction. Task receipts and HTTP decision intents make retries safe. Conflicting responses receive HTTP 409. An unavailable itinerary, declined offer, timeout, resolved connection or need for manual assistance has an explicit outcome. Hotels are suggested only as an airport-agent conversation; hotel booking is outside this demo.

Passenger decisions become typed `passenger-decided` workflow events. Streams extracts them to the plain JSON `customer-responses` topic for inspection. Workflow variables, task inputs/outputs and workflow events use LittleHorse Structs and native arrays, never serialized JSON strings.

The optional `lh-agent-connector` uses STRUCT-to-STRUCT mode and read-only MCP tools at `/mcp/flight-schedules`. Rules mode serves the same advisor task signature inside the backend. Only one advisor worker implementation runs at a time. The backend always validates the chosen option and performs reservations.

## Verify and reset

The real end-to-end smoke test requires the running stack. It books five passengers, verifies selective risk detection through Connect, accepts one offer twice, declines another, waits for a third to expire, checks the preserved PNR and updated revision, repeats a delay, then cancels a trip and verifies cleanup. Use a fresh stack and a shorter response window for faster testing:

```bash
./cleanup.sh --reset
RESPONSE_SECONDS=15 ./setup.sh
python3 simulator/smoke.py --timeout 60
```

Run `./cleanup.sh` to stop while retaining state. `./cleanup.sh --reset` removes this demo's Kafka, PostgreSQL, LittleHorse and Streams state. Run setup again afterward to reseed. Seeding is repeatable and does not reset seat counts or delays on existing flights.

```bash
cd backend
./gradlew test quarkusBuild
cd ../frontend
npm ci
npm run build
```

The Java tests cover native Struct serialization, workflow graph compilation, booking-before-delay and delay-before-booking ordering, missing status deferral, selective detection, stable workflow IDs across repeated delays, itinerary revisions, cancellation index cleanup, and output-topic normalization. See DEVELOPMENT.md for iteration commands and VALIDATION.md for the checks performed on this delivery.

This is a local, unauthenticated presentation application. Run a single backend instance: interactive queries aggregate its assigned stores and do not implement routing to other instances. The fixture is an offline synthetic network, not a real airline timetable. Model quality depends on the selected provider; no paid provider is required for the demo.
