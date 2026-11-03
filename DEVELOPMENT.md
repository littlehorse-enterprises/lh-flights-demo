# Development

Run `./setup.sh` once. It builds Docker images, creates the application topics, enables tenant execution output, registers workflows/tasks/StructDefs, applies the single Connect sink, and loads fixtures.

After Java changes:

```bash
docker compose build backend
docker compose up -d --no-deps --wait backend
./configure-connectors.sh
```

After frontend changes:

```bash
docker compose build frontend
docker compose up -d --no-deps frontend
```

When using model mode, export settings before calling Compose directly:

```bash
set -a
source .env.local
set +a
docker compose up -d --build agent
```

The backend uses Java 21 or newer, Gradle 9.1, Quarkus 3.32.3 and LittleHorse Quarkus 1.3.0. The optional agent build uses Java 25. Source is pinned; its LittleHorse dependency is explicitly overridden to 1.3.0. Default Kafka runtime and Streams libraries are 4.1.1.

```bash
cd backend
./gradlew spotlessApply test quarkusBuild
cd ../frontend
npm ci
npm run build
```

API operations use business entities. `GET /api/passengers`, `/api/flights`, `/api/trips`, `/api/connections/at-risk` and `/api/rebooking-proposals` power the UI. `POST /api/bookings` accepts passengerId, flightNumbers and date, returns a PNR and workflow ID with HTTP 202, and accepts an optional Idempotency-Key. `POST /api/flights/{flightKey}/delay` accepts a total delay in minutes. `POST /api/rebooking-proposals/{id}/response` accepts accepted and optionId, acknowledges delivery to LittleHorse, and leaves completion to the workflow. `POST /api/trips/{pnr}/cancel` starts a cancellation workflow.

```bash
curl -f -H 'Content-Type: application/json' -H 'Idempotency-Key: rehearsal-ava' \
  -d '{"passengerId":"p001","flightNumbers":["LH101","LH220"],"date":"2026-11-05"}' \
  http://localhost:8081/api/bookings
curl -f -H 'Content-Type: application/json' -d '{"minutes":25}' \
  http://localhost:8081/api/flights/LH101-2026-11-05/delay
```

A flight instance is flight number plus UTC departure date. Schedule dates are synthetic; no code compares them with the laptop's clock. Response deadlines use actual wall-clock seconds.

Check service health, connector task state and the customer-responses topic during a rehearsal:

```bash
docker compose ps
docker compose logs --tail 100 backend kafka-connect littlehorse
curl -f http://localhost:8081/q/health/ready
curl -f http://localhost:8083/connectors/broken-connections/status
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server kafka:9092 --topic customer-responses --from-beginning
```

Changing schemas requires a fresh database for this demo; there is no migration framework. Use `./cleanup.sh --reset` and `./setup.sh`. Regenerate the checked-in fixture with `python3 fixtures/generate.py`; fixture loading never scrapes a live airline.

The optional browser check runs a small static server and mocks the airline API. It does not require Docker. Install Playwright only when running this check:

```bash
cd frontend
npm install --no-save --package-lock=false playwright
npx playwright install chromium
npm run build
node tests/ui-smoke.cjs
```

Set CHROMIUM_EXECUTABLE to an existing Chromium executable if necessary. Run simulator/smoke.py against the real Compose stack to verify service integration.
