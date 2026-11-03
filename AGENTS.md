# AGENTS.md

Guidance for coding agents working in this repository.

## Project Purpose

This is a vendor-neutral garbage-service operations demo built around LittleHorse. It simulates ordinary truck activity and six deterministic exception scenarios, then demonstrates the difference between observing an event and coordinating the business response through completion.

Do not introduce customer, prospect, or individual-person branding into this repository. Keep names, IDs, municipalities, systems, and documentation generic.

## Architecture

The runtime is Docker Compose based:

- `kafka`: Apache Kafka 4.3.1 in single-node KRaft mode.
- `littlehorse`: LittleHorse server with a matching `lhctl` binary added by `littlehorse.Dockerfile`.
- `dashboard`: LittleHorse Dashboard.
- `backend`: Quarkus REST application, LittleHorse workflows, and task workers.
- `kafka-connect`: Apache Kafka 4.3.1 running the LittleHorse sink connectors.
- `frontend`: React production build served by Nginx. Nginx proxies `/api` to `backend:8081`.
- `simulator/simulate.py`: host-side Python load generator.
- `simulator/smoke.py`: host-side accelerated end-to-end test.

Do not use Confluent runtime images. Both Kafka broker and Kafka Connect use `apache/kafka`.

## Repository Layout

- `backend/src/main/java/io/littlehorse/demo/workflow/`: workflows, forms, and StructDefs.
- `backend/src/main/java/io/littlehorse/demo/workflow/tasks/`: task beans grouped by business domain.
- `backend/src/main/java/io/littlehorse/demo/api/`: webhook and UI-facing REST resources.
- `backend/src/main/java/io/littlehorse/demo/store/DemoStore.java`: in-memory mock database, ledger, and outcome counters.
- `frontend/`: React/Vite source and Nginx configuration.
- `connectors/`: Kafka Connect REST configurations.
- `simulator/`: deterministic load generator and smoke test.
- `docker-compose.yml`: complete runtime topology.
- `setup.sh`: Docker-only environment setup.
- `configure-connectors.sh`: applies connector configurations using a Dockerized curl client.
- `DEVELOPMENT.md`: service-by-service iteration commands.

## First Startup

The application build and connector build happen inside Docker. Java, Gradle, Node, Git, and curl are not runtime prerequisites on the host.

```bash
./setup.sh
```

Python is only required to run the simulator:

```bash
python3 simulator/simulate.py
```

Useful URLs:

- Operations UI: `http://localhost:5173`
- Quarkus API: `http://localhost:8081`
- LittleHorse Dashboard: `http://localhost:3000`
- Kafka Connect: `http://localhost:8083`
- LittleHorse gRPC: `localhost:2023`

## Development Commands

After backend Java changes, rebuild the image. Restarting alone runs the old image:

```bash
docker compose build backend
docker compose up -d --no-deps --wait backend
```

Backend startup automatically re-registers annotated workflows, tasks, forms, and StructDefs.

After frontend changes:

```bash
docker compose build frontend
docker compose up -d --no-deps --wait frontend
```

After connector JSON changes:

```bash
./configure-connectors.sh
```

After Compose, Dockerfile, bind-mount, or environment changes:

```bash
docker compose up -d --build --force-recreate --wait
```

Validate configuration:

```bash
docker compose config --quiet
```

Run the full smoke test:

```bash
python3 simulator/smoke.py
```

The default smoke run takes about 75 seconds.

## Readiness

LittleHorse HTTP liveness is not sufficient for metadata registration. The server may report liveness while gRPC still returns `UNAVAILABLE: Server Initializing`.

The LittleHorse image includes the matching CLI, and its Compose healthcheck runs:

```bash
lhctl whoami
```

against the internal `localhost:2024` listener. The backend depends on this healthcheck. Do not replace it with `/liveness` unless a separate API-readiness gate is retained.

## LittleHorse Mental Model

- `TaskDef` is a task signature; `TaskRun` is one execution.
- Task workers are long-running application code that poll the LittleHorse server.
- `WfSpec` is a workflow program; `WfRun` is one runtime instance.
- Workflow definition code executes at application startup to compile/register a graph. It does not execute business logic at WfRun runtime.
- `WfRunVariable`, `NodeOutput`, `SpawnedChildWf`, and other DSL objects are symbolic graph references, not normal Java runtime values.
- Business/runtime work belongs in task methods. Do not read databases or call APIs directly from workflow definition code.
- The entrypoint thread determines the visible WfRun lifecycle.

## Quarkus Extension Patterns

The backend uses `io.littlehorse:littlehorse-quarkus:1.3.0` from Maven Central.

Repositories are configured in `backend/build.gradle`:

```gradle
repositories {
    mavenLocal()
    mavenCentral()
}
```

### Workflows

Keep each WfSpec in its own file. Use class-based annotation registration:

```java
@LHWorkflow(MyWorkflow.NAME)
public class MyWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "my-workflow";

    @Override
    public void define(WorkflowThread wf) {
        WfRunVariable input = wf.declareStr("input").required().searchable();
        wf.execute("my-task", input);
    }
}
```

The extension also supports method-level `@LHWorkflow` methods accepting one `WorkflowThread`, but this project intentionally uses one class per workflow.

Current WfSpecs:

- `record-pickup`
- `contact-customer`
- `send-invoice`
- `dispatch-truck`
- `handle-overfilled-can`
- `handle-no-service`

### Tasks

Group task methods by business domain, not one giant task class and not necessarily one class per method:

```java
@LHTask
public class CustomerCommunicationTasks {
    private final DemoStore store;

    public CustomerCommunicationTasks(DemoStore store) {
        this.store = store;
    }

    @LHTaskMethod("lookup-contact-method")
    public String lookupContactMethod(String customerId) {
        return store.lookupContactMethod(customerId);
    }
}
```

Current task domains:

- `PickupTasks`
- `CustomerCommunicationTasks`
- `ServiceOperationsTasks`
- `BillingTasks`
- `CustomerLedgerTasks`

`@LHTask` classes are CDI beans and task methods may use constructor-injected dependencies. They are long-lived and must be thread-safe.

### User Tasks

Forms use `@LHUserTaskForm` and public `@UserTaskField` fields:

```java
@LHUserTaskForm(OperatorRecoveryForm.NAME)
public class OperatorRecoveryForm {
    public static final String NAME = "recover-failed-service";

    @UserTaskField(displayName = "Approved?")
    public boolean approved;
}
```

Use `.withNotes(...)` to attach WfRun-specific context to the task. The frontend must display these notes.

The extension produces `LittleHorseBlockingStub` as a CDI singleton. Inject it into REST resources rather than creating `LHConfig` or a custom producer:

```java
public UserTaskResource(LittleHorseBlockingStub client) {
    this.client = client;
}
```

Do not recreate the deleted `LittleHorseRuntime` manual bootstrap. Do not manually construct `LHTaskWorker`s or loop over workflow registrations.

### StructDefs

Annotate POJOs with `@LHStructDef`:

```java
@LHStructDef("service-completion")
public class ServiceCompletion {
    // no-args constructor and bean getters/setters
}
```

The extension registers StructDefs before WfSpecs. `service-completed` carries a `ServiceCompletion` struct, not an untyped JSON object.

## Workflow Composition

Use a child workflow when the process should be reusable, separately visible, or independently inspectable. Use a child thread only for parallel work inside one WfRun when shared variables are useful.

Start and wait for a child workflow in the same thread:

```java
SpawnedChildWf child = wf.runWf(
        ContactCustomerWorkflow.NAME,
        Map.of("customer-id", customerId));
WfRunVariable result = wf.declareBool("result");
result.assign(wf.waitForChildWf(child));
```

`waitForChildWf()` must execute in the same workflow thread that called `runWf()`.

Child workflow failures propagate to the parent wait node. Technical child failure appears as `CHILD_FAILURE`; business exceptions propagate as their exception name.

## Conditionals

For multiple branches, use the fluent chain:

```java
wf.doIf(method.isEqualTo("PHONE"), phone -> { /* ... */ })
        .doElseIf(method.isEqualTo("TEXT"), text -> { /* ... */ })
        .doElse(push -> { /* ... */ });
```

Do not nest a second `doIfElse()` inside an else branch. The current Java SDK's `doIfElse(...)` returns `void`; fluent branching starts with `doIf(...)`.

Simple binary branches may use `doIf(...).doElse(...)`.

Use lambdas for simple branch bodies. When a conditional chain would otherwise become
unwieldy, use method handles such as `this::handlePhone` instead. In that pattern, keep the
branch's `WfRunVariable` handles as private members of the workflow class, initialize them in
`define()`, and have the handler methods accept the branch `WorkflowThread`:

```java
private WfRunVariable result;

public void define(WorkflowThread wf) {
    result = wf.declareBool("result");
    wf.doIf(condition, this::handleTrue).doElse(this::handleFalse);
}

private void handleTrue(WorkflowThread thread) {
    result.assign(thread.execute("handle-true"));
}
```

## External and Correlated Events

Use an `ExternalEvent` when the sender knows the exact `WfRunId`. Use a `CorrelatedEvent` when the sender knows a business key but not the WfRun ID.

Corrective pickup completion uses a correlated event keyed by `service-stop-id`:

```java
wf.waitForEvent("service-completed")
        .withCorrelationId(serviceStopId)
        .registeredAs(ServiceCompletion.class)
        .timeout(20);
```

Correlated events can arrive before a workflow begins waiting and remain available for correlation. Event senders may retry, so preserve stable keys and idempotent payload handling.

The Kafka correlated-event record key must be the non-empty string correlation ID. The complete JSON value is mapped into the event content.

## Timeouts and Failures

- `ERROR` is a technical/runtime failure.
- `EXCEPTION` is a business-level failure defined by the application.
- Retries handle transient technical failures; retries are not exception handling.
- Event wait timeout is `LHErrorType.TIMEOUT` and must use `handleError`, not `handleException`.
- Use `wf.fail("kebab-case-name", "message")` for business exceptions.

Example:

```java
ExternalEventNodeOutput result = wf.waitForEvent("service-completed")
        .withCorrelationId(serviceStopId)
        .timeout(20)
        .registeredAs(ServiceCompletion.class);

wf.handleError(result, LHErrorType.TIMEOUT, handler -> {
    // recovery flow
});
```

Failure handlers execute in child threads and can access variables visible to the failed thread. If a handler completes, normal flow continues after the failed node.

## Searchable Variables

Use searchable variables for dashboard discovery and API searches:

```java
WfRunVariable customerId = wf.declareStr("customer-id")
        .required()
        .searchable();
```

`SearchWfRunRequest.variableFilters` searches entrypoint-thread variables. For indexed variables in other threads, use variable search APIs.

The simulator uses fixed customer/scenario IDs for discoverability but appends a timestamp run token to event IDs and service-stop IDs. Kafka WfRun connectors use record keys as WfRun IDs, so the suffix prevents `ALREADY_EXISTS` across repeated runs.

## User Task REST Semantics

LittleHorse user-task search returns IDs. Hydrate each with `getUserTaskRun`, then fetch its `UserTaskDef` for form fields.

UserTaskRun IDs may include a parent WfRun chain for child workflows. Do not reconstruct the protobuf ID using only the leaf string. Resolve and retain the full ID returned from search.

Completion result names and types must exactly match `UserTaskField` names and types. The REST resource checks the task definition before submitting optional fields so older task versions remain completable.

## Kafka Connect Contracts

The connector bundle is built from the source revision pinned by `CONNECTOR_REVISION` in `.env`. Despite the upstream Gradle task name `buildConfluentBundle`, the runtime uses Apache Kafka images only.

### WfRun Sink

WfRun connector payloads must be top-level JSON objects. JSON property names must exactly match entrypoint workflow variable names and types.

WfRun ID precedence in the connector is:

1. `wfRunId` string header
2. Kafka string key
3. Generated connector/topic/partition/offset ID

This demo publishes deterministic event IDs as Kafka keys.

### Correlated Event Sink

The service completion connector uses:

```text
connector.class=io.littlehorse.connect.CorrelatedEventSinkConnector
external.event.name=service-completed
key.converter=org.apache.kafka.connect.storage.StringConverter
value.converter=org.apache.kafka.connect.json.JsonConverter
value.converter.schemas.enable=false
```

The connector reads ExternalEventDef metadata during startup to map schemaless JSON into the `ServiceCompletion` StructDef. Therefore:

- Quarkus must register metadata before Kafka Connect starts.
- Compose makes Kafka Connect depend on the healthy backend.
- If metadata changes, rebuild/restart backend before recreating or restarting Kafka Connect.

Customer responses use the same correlated-event connector pattern on the `customer-responses` topic and map into the `CustomerResponse` StructDef using the `eventId` Kafka record key.

## Webhooks and Simulator

Webhook endpoints are under `/webhooks/{type}`:

- `truck-locations`
- `pickups`
- `overfilled-cans`
- `no-service-events`
- `service-exceptions`
- `customer-responses`
- `simulation-started`
- `simulation-stopped`

Ordinary successful pickups intentionally start LittleHorse WfRuns and update the in-memory ledger. Truck location events remain background Kafka activity and update counters without creating WfRuns.

The simulator's six deterministic scenarios must remain repeatable:

1. Overfilled container warning.
2. Additional-service tag purchase.
3. Successful corrective pickup.
4. Timed-out pickup recovered through a real user task.
5. Positive verification leading to proactive corrective pickup.
6. Overfilled container fee assessment and invoice.

## In-Memory State

`DemoStore` represents mock databases and aggregate outcomes. It is intentionally non-durable and resets when the backend container restarts.

Idempotency keys in `DemoStore` include the action type, for example `pickup:<event-id>` and `ledger:<event-id>`, so multiple business actions may use the same event ID without suppressing one another.

Phone interactions must not increment `interactionsWithoutPhoneCall`. Text and push interactions do.

## Verification Checklist

For backend/workflow changes:

```bash
docker compose build backend
docker compose up -d --no-deps --wait backend
./configure-connectors.sh
python3 simulator/smoke.py
```

For frontend-only changes:

```bash
docker compose build frontend
docker compose up -d --no-deps --wait frontend
curl --fail http://localhost:5173/api/outcomes
```

Always run:

```bash
docker compose config --quiet
git diff --check
```

Check service state and logs when startup fails:

```bash
docker compose ps
docker compose logs backend
docker compose logs littlehorse
docker compose logs kafka-connect
```

## Common Pitfalls

- Do not use LittleHorse HTTP liveness as API readiness; use `lhctl whoami`.
- Do not restart backend without rebuilding after Java changes.
- Do not start Kafka Connect before workflow/event metadata exists.
- Do not treat workflow DSL handles as Java runtime values.
- Do not use untyped JSON for `service-completed`; it is a StructDef.
- Do not reconstruct hierarchical UserTaskRun IDs from leaf strings.
- Do not send result fields absent from the concrete UserTaskDef version.
- Do not reuse deterministic Kafka keys across simulator runs without a run suffix.
- Do not introduce Confluent runtime images.
- Do not recreate a manual LittleHorse runtime/bootstrap class.
- Preserve unrelated staged or unstaged user changes.

## Source References

Useful SDK and extension sources in the shared workspace:

- `../littlehorse/sdk-java/src/main/java/io/littlehorse/sdk/wfsdk/`
- `../littlehorse/examples/java/`
- `../lh-quarkus/extensions/littlehorse-quarkus/`
- `../lh-quarkus/examples/`
- `../lh-kafka-connect/examples/wfrun-json/`
- `../lh-kafka-connect/examples/correlated-event-json/`
- `../lh-kafka-connect/examples/correlated-event-struct/`

When SDK behavior is unclear, inspect these sources before inventing an API or relying on memory.
