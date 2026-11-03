# Validation of this delivery

The backend passes 11 JUnit tests and a complete Quarkus production build against LittleHorse Quarkus 1.3.0. Tests use real RocksDB stores in Kafka Streams TopologyTestDriver and native LittleHorse Struct serialization. They cover workflow graph compilation, event normalization, selective connection detection, both event arrival orders, missing status deferral, stale status/booking updates, booking revisions and cancellation index cleanup. Repeated risk events carry the same workflow ID and rely on RunWf idempotence. Advisor tests cover next-day permission, same-day ordering and no-option assistance.

The frontend passes TypeScript checking and a Vite production build. A headless Chromium check with a mocked airline API verified desktop rendering, flight-delay publication, passenger acceptance and final confirmation, no JavaScript errors, and no horizontal overflow at a 390 px viewport. Both desktop screens were visually inspected. The browser check is in frontend/tests/ui-smoke.cjs; it intentionally tests the UI separately from the real service smoke test.

The optional agent connector builds from its pinned source using the explicit LittleHorse 1.3.0 override. The Kafka Connect bundle also builds from pinned source against the 1.3.0 client. The built connector bundle is included under plugins; setup reuses it when its build marker matches. sources.lock records its SHA-256.

Compose configuration, Bash syntax, Python compilation, git whitespace checks, all 524 unique flight keys, 500 passengers, and 3,974 valid connecting fixture itineraries were checked.

Docker is unavailable in the build environment. The actual Compose stack, PostgreSQL transactions, broker/Connect integration, restart restoration, and full simulator/smoke.py execution have therefore not been verified here. No paid provider or local model was invoked. The supplied smoke script must pass on the presentation machine before the demo is treated as rehearsed.
