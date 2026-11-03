package io.littlehorse.demo;

import static org.junit.jupiter.api.Assertions.*;

import io.littlehorse.demo.kafka.*;
import io.littlehorse.demo.model.*;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.*;
import java.time.*;
import java.util.*;
import java.util.Map;
import org.apache.kafka.common.serialization.*;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.*;

class AirlineTopologyTest {
    TopologyTestDriver driver;
    Map<String, TestInputTopic<String, String>> inputs = new HashMap<>();
    Map<String, TestOutputTopic<String, String>> outputs = new HashMap<>();
    FlightLeg inbound = leg("LH101", "RNO", "DEN", "15:15:00Z", "17:00:00Z");
    FlightLeg bos = leg("LH220", "DEN", "BOS", "18:05:00Z", "21:50:00Z");
    FlightLeg sea = leg("LH250", "DEN", "SEA", "19:10:00Z", "21:55:00Z");

    static FlightLeg leg(String number, String origin, String dest, String departure, String arrival) {
        return new FlightLeg(
                number + "-2026-11-05",
                number,
                "2026-11-05",
                origin,
                dest,
                "2026-11-05T" + departure,
                "2026-11-05T" + arrival,
                Instant.parse("2026-11-05T" + departure).minusSeconds(900).toString());
    }

    @BeforeEach
    void start() {
        Properties p = new Properties();
        p.put("application.id", "topology-test");
        p.put("bootstrap.servers", "dummy:9092");
        p.put("state.dir", System.getProperty("java.io.tmpdir") + "/airline-test-" + UUID.randomUUID());
        driver = new TopologyTestDriver(AirlineTopology.build("execution"), p);
        for (String topic : List.of(
                "trip-bookings",
                "flight-bookings-internal",
                "flight-status",
                "flight-status-view",
                "flight-status-internal",
                "affected-trips",
                "broken-connections",
                "customer-responses")) {
            if (!List.of("broken-connections", "customer-responses").contains(topic))
                inputs.put(topic, driver.createInputTopic(topic, new StringSerializer(), new StringSerializer()));
            if (!topic.equals("flight-status"))
                outputs.put(topic, driver.createOutputTopic(topic, new StringDeserializer(), new StringDeserializer()));
        }
    }

    @AfterEach
    void close() {
        driver.close();
    }

    void pump() {
        for (int i = 0; i < 20; i++) {
            boolean progress = false;
            for (String topic : List.of(
                    "trip-bookings",
                    "flight-bookings-internal",
                    "flight-status-view",
                    "flight-status-internal",
                    "affected-trips")) {
                var out = outputs.get(topic);
                while (!out.isEmpty()) {
                    var r = out.readKeyValue();
                    inputs.get(topic).pipeInput(r.key, r.value);
                    progress = true;
                }
            }
            if (!progress) return;
        }
        fail("Topic loop did not settle");
    }

    void status(FlightLeg leg, int delay, long sequence) {
        inputs.get("flight-status").pipeInput(leg.getFlightKey(), Json.write(new FlightStatus(leg, delay, sequence)));
        pump();
    }

    void book(String id, FlightLeg destination, int revision) {
        inputs.get("trip-bookings")
                .pipeInput(id, Json.write(new BookedTrip(id, "p001", revision, "CONFIRMED", new FlightLeg[] {
                    inbound, destination
                })));
        pump();
    }

    @Test
    void selectivelyDetectsWithStableWorkflowIdsForRepeatedDelays() {
        status(inbound, 0, 1);
        status(bos, 0, 1);
        status(sea, 0, 1);
        book("bos", bos, 1);
        book("sea", sea, 1);
        assertTrue(outputs.get("broken-connections").isEmpty());
        status(inbound, 10, 2);
        assertTrue(outputs.get("broken-connections").isEmpty());
        status(inbound, 25, 3);
        var repeatedRisks = outputs.get("broken-connections").readRecordsToList();
        assertFalse(repeatedRisks.isEmpty());
        for (var record : repeatedRisks) {
            assertEquals("bos", record.key());
            assertEquals(
                    "recover-bos-v1",
                    new String(
                            record.headers().lastHeader("wfRunId").value(), java.nio.charset.StandardCharsets.UTF_8));
        }
        var broken = repeatedRisks.getFirst();
        assertEquals("bos", broken.key());
        assertEquals(Map.of("passengerId", "p001", "tripId", "bos"), Json.read(broken.value(), Map.class));
        assertEquals(
                "recover-bos-v1",
                new String(broken.headers().lastHeader("wfRunId").value(), java.nio.charset.StandardCharsets.UTF_8));
        assertTrue(outputs.get("broken-connections").isEmpty());
        status(inbound, 30, 4);
        var repeated = outputs.get("broken-connections").readRecordsToList();
        assertFalse(repeated.isEmpty());
        for (var record : repeated)
            assertEquals(
                    "recover-bos-v1",
                    new String(
                            record.headers().lastHeader("wfRunId").value(), java.nio.charset.StandardCharsets.UTF_8));
        status(inbound, 0, 2);
        assertTrue(outputs.get("broken-connections").isEmpty());
        assertEquals(
                "AT_RISK",
                Json.read(
                                driver.<String, String>getKeyValueStore("connection-risk")
                                        .get("bos"),
                                AirlineTopology.Risk.class)
                        .status());
    }

    @Test
    void delayBeforeBookingIsDetected() {
        status(inbound, 25, 3);
        status(bos, 0, 1);
        book("late-booking", bos, 1);
        assertEquals("late-booking", outputs.get("broken-connections").readKeyValue().key);
    }

    @Test
    void missingFlightDataDefersInsteadOfMarkingSafe() {
        book("pending", bos, 1);
        assertNull(driver.<String, String>getKeyValueStore("connection-risk").get("pending"));
        status(inbound, 25, 3);
        assertTrue(outputs.get("broken-connections").isEmpty());
        status(bos, 0, 1);
        assertEquals("pending", outputs.get("broken-connections").readKeyValue().key);
    }

    @Test
    void itineraryChangesAndCancellationRemoveOldMemberships() {
        status(inbound, 0, 1);
        status(bos, 0, 1);
        status(sea, 0, 1);
        book("changed", bos, 1);
        book("changed", sea, 2);
        assertNull(driver.<String, String>getKeyValueStore("flight-to-trips").get(bos.getFlightKey()));
        book("changed", bos, 1); // stale booking must not restore BOS membership
        status(bos, 120, 2);
        assertTrue(outputs.get("broken-connections").isEmpty());
        inputs.get("trip-bookings")
                .pipeInput(
                        "changed",
                        Json.write(new BookedTrip("changed", "p001", 3, "CANCELLED", new FlightLeg[] {inbound, sea})));
        pump();
        assertNull(driver.<String, String>getKeyValueStore("flight-to-trips").get(inbound.getFlightKey()));
        assertNull(driver.<String, String>getKeyValueStore("connection-risk").get("changed"));
    }

    @Test
    void newBookingRevisionCanStartAnotherRecovery() {
        status(inbound, 25, 2);
        status(bos, 0, 1);
        book("revised", bos, 1);
        outputs.get("broken-connections").readRecordsToList();
        book("revised", bos, 2);
        var r = outputs.get("broken-connections").readRecord();
        assertEquals(
                "recover-revised-v2",
                new String(r.headers().lastHeader("wfRunId").value(), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Test
    void extractsNativeStructBookingAndPassengerDecision() {
        var input = driver.createInputTopic("execution", new StringSerializer(), new ByteArraySerializer());
        BookedTrip trip = new BookedTrip("abc", "p001", 1, "CONFIRMED", new FlightLeg[] {inbound, bos});
        input.pipeInput("x", event("trip-booked", LHLibUtil.objToVarVal(trip)).toByteArray());
        assertEquals("abc", outputs.get("trip-bookings").readKeyValue().key);
        input.pipeInput(
                "x",
                event(
                                "passenger-decided",
                                LHLibUtil.objToVarVal(new PassengerDecision("offer-abc", "abc", true, "direct")))
                        .toByteArray());
        assertEquals("abc", outputs.get("customer-responses").readKeyValue().key);
        input.pipeInput(
                "x",
                OutputTopicRecord.newBuilder()
                        .setWfRun(WfRun.newBuilder())
                        .build()
                        .toByteArray());
        assertTrue(outputs.get("trip-bookings").isEmpty());
    }

    static OutputTopicRecord event(String name, VariableValue value) {
        return OutputTopicRecord.newBuilder()
                .setWorkflowEvent(WorkflowEvent.newBuilder()
                        .setId(WorkflowEventId.newBuilder()
                                .setWorkflowEventDefId(
                                        WorkflowEventDefId.newBuilder().setName(name)))
                        .setContent(value))
                .build();
    }
}
