package io.littlehorse.demo.api;

import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.littlehorse.demo.kafka.AirlineStreams;
import io.littlehorse.demo.model.*;
import io.littlehorse.demo.store.AirlineStore;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.*;
import java.util.Comparator;
import java.util.Map;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class AirlineResource {
    final AirlineStore store;
    final AirlineStreams streams;
    final LittleHorseBlockingStub client;

    public AirlineResource(AirlineStore store, AirlineStreams streams, LittleHorseBlockingStub client) {
        this.store = store;
        this.streams = streams;
        this.client = client;
    }

    @GET
    @Path("/passengers")
    public Object passengers() {
        return store.passengers();
    }

    @POST
    @Path("/passengers")
    public void passengers(List<AirlineStore.Passenger> passengers) {
        store.seedPassengers(passengers);
    }

    @GET
    @Path("/flights")
    public Object flights() {
        var inventory = store.inventory();
        return streams.statuses().stream()
                .sorted(Comparator.comparing(FlightStatus::departure))
                .map(s -> Map.of(
                        "flight",
                        s.flight(),
                        "delayMinutes",
                        s.delayMinutes(),
                        "sequence",
                        s.sequence(),
                        "seats",
                        inventory.getOrDefault(s.flight().getFlightKey(), 0)))
                .toList();
    }

    @POST
    @Path("/flights")
    public void schedules(List<AirlineStore.ScheduledFlight> flights) {
        store.seedFlights(flights);
        for (var f : store.flights()) streams.publishStatus(f);
    }

    public record Delay(int minutes) {}

    @POST
    @Path("/flights/{key}/delay")
    public Object delay(@PathParam("key") String key, Delay request) {
        FlightStatus status = store.delay(key, request.minutes());
        streams.publishStatus(status);
        return status;
    }

    public record Booking(String passengerId, String[] flightNumbers, String date) {}

    @POST
    @Path("/bookings")
    public Response book(Booking request, @HeaderParam("Idempotency-Key") String key) {
        String id = key == null
                ? UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                : key.toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9-]{1,50}"))
            throw new BadRequestException("Idempotency-Key must contain 1–50 lowercase letters, digits or hyphens");
        store.initial(id, request.passengerId(), request.flightNumbers(), request.date());
        store.recordBookingRequest(id, request);
        var r = RunWfRequest.newBuilder()
                .setWfSpecName("booking")
                .setId("book-" + id)
                .putVariables("tripId", LHLibUtil.objToVarVal(id))
                .putVariables("passengerId", LHLibUtil.objToVarVal(request.passengerId()))
                .putVariables(
                        "flightNumbers",
                        LHLibUtil.objToVarValAsNativeArray(
                                request.flightNumbers(),
                                String[].class,
                                io.littlehorse.sdk.common.adapter.LHTypeAdapterRegistry.empty()))
                .putVariables("date", LHLibUtil.objToVarVal(request.date()))
                .build();
        run(r);
        return Response.accepted(Map.of("tripId", id, "workflowId", "book-" + id))
                .build();
    }

    @GET
    @Path("/trips")
    public Object trips() {
        return streams.trips();
    }

    @GET
    @Path("/trips/{id}")
    public Object trip(@PathParam("id") String id) {
        var trip = store.trip(id);
        if (trip == null) throw new NotFoundException("Trip not found");
        return trip;
    }

    @POST
    @Path("/trips/{id}/cancel")
    public Response cancel(@PathParam("id") String id) {
        if (store.trip(id) == null) throw new NotFoundException("Trip not found");
        run(RunWfRequest.newBuilder()
                .setWfSpecName("cancel-booking")
                .setId("cancel-" + id)
                .putVariables("tripId", LHLibUtil.objToVarVal(id))
                .build());
        return Response.accepted().build();
    }

    @GET
    @Path("/connections/at-risk")
    public Object risks() {
        return streams.risks().stream()
                .filter(r -> r.status().equals("AT_RISK"))
                .toList();
    }

    @GET
    @Path("/rebooking-proposals")
    public Object proposals() {
        return store.proposals();
    }

    @GET
    @Path("/rebooking-proposals/{id}")
    public Object proposal(@PathParam("id") String id) {
        return store.proposal(id);
    }

    public record ResponseRequest(boolean accepted, String optionId) {}

    @POST
    @Path("/rebooking-proposals/{id}/response")
    public Response respond(@PathParam("id") String id, ResponseRequest request) {
        var decision = store.decision(id, request.accepted(), request.optionId() == null ? "" : request.optionId());
        try {
            client.putCorrelatedEvent(PutCorrelatedEventRequest.newBuilder()
                    .setKey(id)
                    .setExternalEventDefId(ExternalEventDefId.newBuilder().setName("passenger-response"))
                    .setContent(LHLibUtil.objToVarVal(decision))
                    .build());
        } catch (StatusRuntimeException e) {
            // The store rejects conflicting decisions; the same event may already be delivered.
            if (e.getStatus().getCode() != Status.Code.ALREADY_EXISTS) throw e;
        }
        return Response.accepted(Map.of("status", "DELIVERED")).build();
    }

    void run(RunWfRequest request) {
        try {
            client.runWf(request);
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() != Status.Code.ALREADY_EXISTS) throw e;
        }
    }
}
