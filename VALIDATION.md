# Validation — November 3, 2026

Verified on Linux x86-64 with Docker Engine 29.7.2 and Compose 5.5.0, using rules mode.

- **Setup:** fresh Docker volumes, non-interactive rules selection, connector build from pinned source, plugin export, service startup, connector registration, and fixture loading. Repeated setup/reseeding also tested. This host's Docker build DNS required `DOCKER_BUILD_NETWORK=host`.
- **Backend:** `./gradlew spotlessApply test quarkusBuild` passes all 12 tests, including a regression check that the booking task accepts a native LittleHorse string array.
- **Frontend:** `npm ci && npm run build` passes. Mocked browser smoke passes for delay publication, acceptance, and mobile layout.
- **Real end-to-end smoke:** `python3 simulator/smoke.py --timeout 60` passes with `RESPONSE_SECONDS=15`. Covers booking → workflow output → Streams → Connect → recovery proposals, safe SEA connections, acceptance and duplicate response, decline, timeout, preserved PNR/revision, repeated delays, and cancellation cleanup.
- **Live Chromium:** the real frontend publishes a delay, displays a recovery proposal, accepts the itinerary, and shows confirmation with booking revision 2. Its workflow link opens the actual LittleHorse Dashboard run. No application JavaScript errors; Dashboard 1.3.0 logs a React hydration warning while rendering the run.
- **Traffic:** randomized DEN-hub bookings submitted through `simulator/simulate.py`.
- **Restart:** backend restart retained identical query results for 17 trips, 2 at-risk connections, and 524 flight statuses.
- **Static checks:** Bash syntax, Python compilation, Compose configuration (rules and model/Ollama profiles), and `git diff --check` pass.

Fixed during runtime verification: missing LZ4 codec for execution records; incorrect JSON-array task signature; HTTP 500 on duplicate correlated events; root-owned exported plugins blocking Linux setup; Python error reporting for failed GET requests. Setup now checks Docker availability before prompting and saves advisor configuration after validating input.

OpenAI, Anthropic, and Ollama model execution have not been tested in this verification.
