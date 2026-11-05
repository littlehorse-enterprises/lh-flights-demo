# Connection Rescue

Try [LittleHorse](https://littlehorse.io) by helping airline passengers recover from missed connections. Book flights, inject a delay, and watch workflows recommend alternatives, wait for each passenger, and complete their rebooking.

Airline data is synthetic; LittleHorse, Kafka, Kafka Streams, Kafka Connect, and PostgreSQL run locally for real.

## 1. Start the demo

**You need:** a running Docker installation with Compose **v2.30+**, Bash (macOS/Linux or WSL2), and Python **3.10+** for booking traffic. Allocate at least **8 GB RAM** to Docker and keep ports **2023, 3000, 5173, 8081, 8083, 19092** available. Java and Node are built inside Docker.

From this repository's directory:

```bash
./setup.sh
```

Choose **1 — rules** for the simplest first run: no API key or model download required. OpenAI, Anthropic, and local Ollama/Qwen models are optional; local models need extra memory and disk. Setup asks you to choose on every run; configuration is written to Git-ignored files for the running services.

First startup downloads dependencies and builds the demo; allow several minutes. Wait until setup prints the application URLs.

## 2. Book, delay, and rescue

1. Open the **[control room](http://localhost:5173/backoffice)**.
2. In another terminal, from this repository, start bookings:

   ```bash
   python3 simulator/simulate.py --hub DEN
   ```

3. Wait for **BOOKED TRIPS** to increase. Under **Inject a delay**, select a flight **arriving in DEN** with booked passengers. Flights with bookings appear first.
4. Choose **120 minutes**, then **Publish flight status**. Within a few seconds, affected passengers appear under **Passengers who need a hand**. Unaffected trips stay on track.
5. Open a passenger card and **Confirm this itinerary**. Wait for **New itinerary confirmed**. Try **Keep my current booking** on another card, or leave an offer unanswered to see its **three-minute timeout**.
6. Click **View workflow** to inspect the execution in the **[LittleHorse Dashboard](http://localhost:3000)**. LittleHorse coordinates the recommendation, passenger wait, and reservation child workflow.

The simulator only books passengers; delays are controlled in the UI. Press **Ctrl-C** to stop booking traffic. Flight schedules use November 5–6, 2026 UTC; response deadlines use the actual clock.

## Stop or start over

```bash
./cleanup.sh  # Remove all demo containers, volumes, images, plugins, and local configuration
./setup.sh    # Start fresh and reload schedules
```

To change advisor, run `./setup.sh` again and choose a different option.

## Troubleshooting and verification

If startup fails, check `docker compose ps` and `docker compose logs --tail 100 backend kafka-connect littlehorse`. Resolve the reported issue, then rerun `./setup.sh`; setup can be repeated. If no offers appear, check that the selected flight has bookings and arrives at the connection airport.

For a repeatable end-to-end check (deletes demo data):

```bash
./cleanup.sh
RESPONSE_SECONDS=15 ./setup.sh
python3 simulator/smoke.py --timeout 60
```

Run `./setup.sh` afterward to restore the normal three-minute response window.

See **[DEVELOPMENT.md](DEVELOPMENT.md)** for architecture, API examples, build commands, and Docker networking help; **[VALIDATION.md](VALIDATION.md)** records what has been tested.
