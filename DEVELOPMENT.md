# Development Guide

The runtime services are containerized. The Python simulator remains a host-side process that sends HTTP events to the Quarkus container.

## First Startup

Run the complete setup from the repository root:

```bash
./setup.sh
```

This builds the connector bundle and both application images, starts Compose, creates Kafka topics, and registers the connectors.

Useful URLs:

- Operations UI: `http://localhost:5173`
- Quarkus API: `http://localhost:8081`
- LittleHorse Dashboard: `http://localhost:3000`
- Kafka Connect: `http://localhost:8083`

## Change Backend Code

Changes to Java workflows, task beans, REST resources, or backend configuration require rebuilding the backend image. A plain `docker compose restart backend` only restarts the old image.

```bash
docker compose build backend
docker compose up -d --no-deps --wait backend
```

The Quarkus startup recorder registers annotated workflows, tasks, forms, and StructDefs each time the backend container starts.

For workflow changes, reconfigure connectors if the connector was previously stopped or recreated:

```bash
./configure-connectors.sh
```

Check registration and startup errors:

```bash
docker compose logs -f backend
lhctl get wfSpec contact-customer
```

## Change Frontend Code

The production UI is built into an Nginx image. Rebuild and recreate only that service:

```bash
docker compose build frontend
docker compose up -d --no-deps --wait frontend
```

The UI calls the backend through Nginx at `/api`, so no frontend environment change is needed for normal local development.

## Change Compose Configuration

After editing `docker-compose.yml`, Dockerfiles, `connect-distributed.properties`, or service environment variables:

```bash
docker compose up -d --build --wait
```

Use `--force-recreate` when an environment variable or bind mount change is not being picked up:

```bash
docker compose up -d --build --force-recreate --wait
```

Validate the merged Compose configuration without starting services:

```bash
docker compose config --quiet
```

## Change Connector Configurations

Changes to files under `connectors/` do not require a container rebuild. Apply them to the running Kafka Connect service:

```bash
./configure-connectors.sh
```

Inspect connector health:

```bash
curl http://localhost:8083/connectors/pickups/status
curl http://localhost:8083/connectors/overfilled-cans/status
curl http://localhost:8083/connectors/no-service-events/status
curl http://localhost:8083/connectors/service-exceptions/status
curl http://localhost:8083/connectors/customer-responses/status
```

Changes to the connector source revision in `.env` require rebuilding the connector bundle. Remove the generated plugin directory and rerun setup:

```bash
rm -rf plugins/lh-kafka-connect
./setup.sh
```

The bundle is built inside Docker; no host Java or Gradle installation is required.

## Run the Simulator

Start the simulator after the backend and connectors are healthy:

```bash
python3 simulator/simulate.py
```

Use a shorter run while iterating:

```bash
python3 simulator/simulate.py --duration 75
```

The simulator adds a run suffix to event IDs, so repeated runs do not reuse LittleHorse WfRun IDs.

## Verify Changes

Run backend tests locally when Java is available:

```bash
./backend/gradlew -p backend test
```

The normal end-to-end verification uses the containerized backend:

```bash
python3 simulator/smoke.py
```

The smoke test validates connector health, all six scenarios, customer responses, business counters, user-task recovery, and ledger activity.

## Logs and Inspection

Follow one service:

```bash
docker compose logs -f backend
docker compose logs -f kafka-connect
```

Inspect all service status:

```bash
docker compose ps
```

Search workflow runs:

```bash
lhctl search wfRun contact-customer
lhctl search wfRun handle-overfilled-can
lhctl search wfRun handle-no-service
```

## Reset

Stop containers while preserving their volumes:

```bash
docker compose down
```

Remove containers and local Kafka/LittleHorse state for a clean demo:

```bash
./cleanup.sh
```
