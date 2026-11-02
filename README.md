# Garbage Service Operations Demo

The demo simulates 100 trucks, sends webhook events through Kafka, starts LittleHorse workflows with Kafka Connect, and uses correlated events to verify physical service. A control room shows business outcomes and lets an operator complete recovery user tasks.

## Architecture

- Quarkus receives webhooks, publishes Kafka records, hosts mock in-memory systems, and exposes the operations API.
- The `contact-customer` child workflow looks up each customer's preferred channel, sends the message, and waits for a customer-service user task or correlated customer response when required.
- Kafka Connect starts `WfRun`s for pickups and service exceptions.
- Correlated-event connectors map Kafka record keys to waiting `service-stop-id` and customer-response events.
- `service-completed` uses the registered `service-completion` StructDef.
- `customer-responded` uses the registered `customer-response` StructDef.
- React provides the outcome dashboard and user-task inbox.
- Python simulates ordinary route traffic plus six deterministic scenarios.

## Requirements

- Docker with Compose
- Python 3.10 or newer

The first setup builds the struct-capable connector from the source revision pinned in `.env`, compiles Quarkus inside Docker, and starts both application containers. The backend uses the `littlehorse-quarkus:1.2-SNAPSHOT` extension, which auto-registers annotated workflows, task workers, forms, and StructDefs. The Python simulator remains the only non-containerized runtime component.

See [`DEVELOPMENT.md`](DEVELOPMENT.md) for the service-by-service iteration workflow.

## Start

```bash
./setup.sh
```

Open:

- Operations control room: `http://localhost:5173`
- LittleHorse dashboard: `http://localhost:3000`
- Quarkus API: `http://localhost:8081`
- Kafka Connect: `http://localhost:8083`

Run the five-minute simulation from another terminal:

```bash
python3 simulator/simulate.py
```

The fixed customer IDs are `customer-demo-001` through `customer-demo-005`. Event and service-stop IDs include a run suffix so the demo can be repeated without deleting LittleHorse state.

## Scenarios

1. Overfilled container produces a policy warning without waiting for a response.
2. Overfilled container produces an accepted additional-service tag purchase after a text response.
3. Missed service produces an accepted and verified corrective pickup after a customer-service phone task.
4. Corrective pickup times out, creates a real operator user task with workflow notes, dispatches a replacement, and verifies recovery.
5. A no-service webhook represents positive verification, waits for a push response, and produces a proactive paid pickup.
6. An overfilled container consults local rules and sends an assessed fee invoice.

Ordinary successful pickups also start `record-pickup` workflows and write to the in-memory customer ledger.

## Smoke Test

With infrastructure, backend, and connectors running:

```bash
python3 simulator/smoke.py
```

The accelerated test takes about 75 seconds and validates connector health, all six scenarios, customer responses, revenue, task-based recovery, and ledger activity. The simulator remains a host-side Python process and sends events to the containerized Quarkus API.

## Cleanup

```bash
./cleanup.sh
```
