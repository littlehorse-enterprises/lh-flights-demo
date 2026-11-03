# Connection Rescue

This repository is an airline connection-recovery demo. Follow the current README and the user's latest feedback. Keep implementation small and domain-oriented.

Use LittleHorse server/client/Quarkus 1.3.0. Use annotated Quarkus workflows and task beans; registration is automatic. Use typed Structs and native arrays throughout workflow variables, tasks and events. JSON belongs at HTTP, Kafka and database boundaries. Workflow definitions compile graphs at startup; runtime work belongs in tasks. Do not manually bootstrap workers or metadata registration.

Inspect upstream source under `repos/littlehorse`, `repos/lh-quarkus`, `repos/lh-agent-connector` and `repos/lh-kafka-connect` when available; do not inspect the Gradle cache for API guidance. The source revisions and compatibility choices are in sources.lock.

Keep vanilla Kafka Streams embedded in the backend, with changelog-backed partitioned trip/index/risk stores and a global flight-status view. Co-partition the three-partition internal topics. A one-partition flight-status source must be repartitioned for the flight index. Handle missing or stale statuses explicitly. Repeated broken-connection records use the same workflow ID per booking revision; RunWf provides idempotence. Do not add a duplicate-suppression state store.

Use PostgreSQL for scheduling, inventory, PNRs, proposals and idempotency receipts. Reserve seats and update bookings in one short transaction. Do not hold database locks while calling a model or waiting for a passenger. The booking and rebooking workflows share a reservation child workflow. Rebooking retains the existing PNR.

The simulator only submits randomized bookings. Delays are injected in the back-office UI. The passenger route shows recommendations and accepts/declines through an HTTP call that synchronously delivers a typed LittleHorse correlated event. LittleHorse owns the wait and timeout. There is one Kafka Connect sink, from broken-connections to reaccommodate-customer. Do not add baggage workflows or passenger callback topics.

Use Apache Kafka runtime images. Docker builds are the default startup path. Keep credentials in ignored configuration. Rules mode must work without a model. Model mode uses the real agent connector with the same task signature; enforce the choice and inventory in ordinary workers.

After changes, run backend tests/build, the frontend build, `docker compose config --quiet`, `git diff --check` and, where Docker is available, the end-to-end smoke test. Report unavailable checks accurately. Do not claim the smoke test passed without running it. Preserve user changes unrelated to this conversion.
