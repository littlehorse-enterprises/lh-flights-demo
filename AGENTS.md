# Connection Rescue

Keep this airline connection-recovery demo small, domain-oriented, and approachable. Follow the current README and latest user feedback; preserve unrelated changes.

## Goal and presentation

Built for the November 5, 2026 Current session, **“Choreography and Orchestration: React to Kafka Events in Real-Time with Kafka Connect and Business-as-Code,”** this is also a first hands-on LittleHorse experience. Target a reproducible six-minute presentation: **one flight delay starts independent passenger recovery workflows while safe connections remain unaffected.**

1. Setup seeds passengers, schedules, and inventory. Randomized booking traffic creates PNRs through workflows that emit typed booking facts.
2. The presenter delays a booked flight in the back office. Streams correlates flight statuses with workflow-produced bookings; only at-risk `passengerId`/`tripId` pairs reach `broken-connections`.
3. One Connect sink starts `reaccommodate-customer` per affected booking. Workers look up current facts; an advisor recommends feasible alternatives with an explanation. Models rank by free-text preferences; rules choose the next feasible departure, same day before next day.
4. Pending cards open a passenger route showing recommendations, alternatives, and deadlines. Demonstrate acceptance, decline, and timeout progressing independently.
5. LittleHorse owns waits and continuation, even after the browser closes. Acceptance invokes the shared reservation child workflow, revalidates inventory, and updates the existing PNR. Response delivery is not booking confirmation.
6. Inspect actual Dashboard executions and typed workflow events flowing through LittleHorse's output into Streams, including passenger decisions.

Streams detects; Connect triggers; LittleHorse coordinates workers, optional agents, child workflows, and human responses. The model is an observable decision worker; ordinary workers enforce feasibility/inventory and passengers control acceptance. Airline data, systems, and notifications are synthetic; platform execution and configured model calls are real. Require no paid airline API/model or hosted fonts/map tiles. Show actual outcomes and recommendation sources, never fake progress or optimistic success counters.

## UX and scope

Use an airline-style map, departure board, delay controls, affected-passenger cards, and separate passenger route; keep safe trips visible. Passenger screens focus on decisions; technical details use developer views and real Dashboard links. Preserve the subtle ~12pt footer reference, “Let us love one another. · 1 John 4:7.”

Model passengers, dated flights, PNRs, inventory, proposals, and responses—not presentation scenarios. No scenario/journey IDs, scenario endpoints, or redundant trigger snapshots. Offer same-day/allowed next-day alternatives and honest unavailable/manual-assistance outcomes. Hotel suggestions are informational; hotel booking and baggage workflows are excluded. Past-booking MCP tools are follow-up work.

## Architecture

- Use LittleHorse server/client/Quarkus **1.3.0**, annotated workflows/tasks with automatic registration, typed Structs and native arrays throughout tasks, variables, and events. Workflow definitions compile graphs at startup; runtime work belongs in tasks. No manual worker/metadata bootstrap or JSON-in-String/JSON_OBJ substitutes.
- PostgreSQL owns schedules, inventory, bookings, proposals, and idempotency receipts. Reserve seats/update bookings atomically in short transactions; never hold locks across model calls or passenger waits. Booking/rebooking share a reservation child workflow; retain the PNR.
- Embed vanilla Streams in the backend: changelog-backed partitioned trip/index/risk stores, global flight-status view, and interactive queries—not database UI projections. Co-partition three-partition internal topics; repartition the one-partition status source for the flight index. Handle missing/stale statuses explicitly. Stable workflow IDs per booking revision provide RunWf idempotence; no duplicate-suppression store.
- Business topics use plain JSON; no Schema Registry framing, schema/payload wrappers, or `schemaVersion` envelopes. Transport metadata belongs in headers. LittleHorse execution output is native Protobuf.
- The simulator only submits randomized bookings; delays come from the UI. Passenger HTTP responses synchronously deliver typed correlated events directly to LittleHorse. No callback topics/connectors; one recovery sink only.
- Use Apache Kafka images and Docker builds. Rules mode needs no model; model mode uses the real agent connector with the same task signature and read-only schedule MCP lookup. Ordinary workers validate recommendations and inventory.
- For API guidance, inspect workspace `repos/{littlehorse,lh-quarkus,lh-agent-connector,lh-kafka-connect}`, not Gradle caches. `sources.lock` records revisions/compatibility.

## Setup and cleanup

Keep README concise: prerequisites, setup, UI walkthrough, cleanup. Architecture/build details belong in DEVELOPMENT.md; dated results in VALIDATION.md.

- Setup prompts **every interactive run**; non-interactive runs default to rules. Choices: rules, OpenAI, Anthropic, Ollama Qwen 0.6B/4B. Run exactly one advisor; stop previous agent/Ollama services when switching. Ignored `.env.local`/`.agent.env` configure services, not prompt skipping. Never commit keys.
- Cleanup loads the saved profile and targets only Compose project **`connection-rescue`**, removing its containers, volumes/models, service images, connector-builder image, exported plugins, and local configuration. No reset flag, retain-state mode, wildcard profiles, other-demo shutdowns, or global Docker pruning.
- For Linux Docker build DNS problems: `DOCKER_BUILD_NETWORK=host ./setup.sh`; runtime networking stays on Compose.
- Control room: http://localhost:5173/backoffice. Dashboard: http://localhost:3000. Traffic: `python3 simulator/simulate.py --hub DEN`.

## Approval and runtime pitfalls

Only `PENDING` proposals populate passenger cards and wait. No feasible replacement yields `MANUAL_ASSISTANCE`; a resolved connection yields `RESOLVED`; both finish automatically. Completion does not imply acceptance/rebooking. Rules and model modes both require passenger approval. Large delays on random bookings may eliminate all options.

Known approval fixture: restore LH101 to on-time on **2026-11-05**, book p001 on **LH101 → LH220**, await confirmation, then delay LH101 **25 minutes**, leaving other flights on-time. Ten minutes is safe; 25 breaks BOS while LH250/SEA remains safe. Repeat with a new booking: another delay cannot reopen a completed recovery for the same revision. Schedules use synthetic November 5–6 UTC dates; deadlines use wall time. Normal response window: **180 seconds**; smoke: `RESPONSE_SECONDS=15`. Rerun setup without the override afterward.

Preserve fixes: LZ4 runtime codec for compressed execution records; `@LHType(isLHArray = true)` on booking flight numbers; tolerate identical-response `ALREADY_EXISTS` while rejecting conflicting decisions; export plugins with host ownership; use Python `Request.get_method()` for inferred GET errors.

## Context and verification

Task history/summary: `../../context/tasks/2026-11-01-airline-demo/` when available:

- [01 original proposal](../../context/tasks/2026-11-01-airline-demo/01-llm-generated-task.md): presentation story, RNO–DEN/BOS-versus-SEA fixture, agent role, real integrations.
- [02 human corrections](../../context/tasks/2026-11-01-airline-demo/02-human-feedback.md): authoritative architecture/scope changes above.
- [03 revised draft](../../context/tasks/2026-11-01-airline-demo/03-opencode-draft.md), [04 human refinements](../../context/tasks/2026-11-01-airline-demo/04-human-feedback.md): UI delays, separate routes, randomized booking-only traffic.

Latest user/human feedback overrides proposals. Do not revive H2, stringified workflow JSON, baggage, callback connectors, scenarios, database UI projections, or mutable snapshots. Continue in `lh-demo-flights` despite older directory names; code/locks establish actual names/versions, not proposal claims.

For runtime changes: backend `./gradlew test quarkusBuild`; frontend `npm ci && npm run build`; `docker compose config --quiet`; `git diff --check`; with Docker, `python3 simulator/smoke.py --timeout 60` against a 15-second stack. Do not run destructive cleanup for documentation checks; honor requests to skip execution/tests. Report unavailable checks and never claim unrun smoke/model success.

Earlier rules-mode verification passed real smoke, 12 backend tests, frontend build, live browser acceptance, and restart restoration. Later always-prompt setup/destructive cleanup edits were not executed/tested as requested. VALIDATION.md records dated results, not proof of later edits.
