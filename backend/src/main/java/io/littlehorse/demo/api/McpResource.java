package io.littlehorse.demo.api;

import io.littlehorse.demo.kafka.AirlineStreams;
import io.littlehorse.demo.kafka.Json;
import io.littlehorse.demo.store.AirlineStore;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.*;
import java.util.*;

/** Stateless, read-only MCP Streamable HTTP endpoint. No booking tool is exposed. */
@Path("/mcp/flight-schedules")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class McpResource {
    final AirlineStreams streams;
    final AirlineStore store;

    public McpResource(AirlineStreams streams, AirlineStore store) {
        this.streams = streams;
        this.store = store;
    }

    @POST
    public Response call(Map<String, Object> request) {
        Object id = request.get("id");
        if (id == null) return Response.accepted().build();
        String method = String.valueOf(request.get("method"));
        Object result;
        switch (method) {
            case "initialize" ->
                result = Map.of(
                        "protocolVersion",
                        "2025-03-26",
                        "capabilities",
                        Map.of("tools", Map.of()),
                        "serverInfo",
                        Map.of("name", "airline-schedules", "version", "1.0.0"));
            case "ping" -> result = Map.of();
            case "tools/list" ->
                result = Map.of(
                        "tools",
                        List.of(Map.of(
                                "name",
                                "find_flights",
                                "description",
                                "Look up available flight schedules, live delays and seat availability. Times are synthetic UTC.",
                                "inputSchema",
                                Map.of(
                                        "type",
                                        "object",
                                        "properties",
                                        Map.of(
                                                "origin",
                                                Map.of("type", "string"),
                                                "destination",
                                                Map.of("type", "string")),
                                        "required",
                                        List.of("origin", "destination")))));
            case "tools/call" -> {
                Map<?, ?> params = (Map<?, ?>) request.getOrDefault("params", Map.of());
                if (!"find_flights".equals(params.get("name"))) return error(id, -32602, "Unknown tool");
                Map<?, ?> args = (Map<?, ?>) params.get("arguments");
                if (args == null) return error(id, -32602, "Arguments required");
                var inventory = store.inventory();
                var flights = streams.statuses().stream()
                        .filter(f -> f.flight().getOrigin().equals(args.get("origin"))
                                && f.flight().getDestination().equals(args.get("destination")))
                        .map(f -> Map.of(
                                "flight",
                                f.flight(),
                                "delayMinutes",
                                f.delayMinutes(),
                                "seats",
                                inventory.getOrDefault(f.flight().getFlightKey(), 0)))
                        .toList();
                result = Map.of(
                        "content", List.of(Map.of("type", "text", "text", Json.write(flights))), "isError", false);
            }
            default -> {
                return error(id, -32601, "Method not found");
            }
        }
        return Response.ok(Map.of("jsonrpc", "2.0", "id", id, "result", result)).build();
    }

    static Response error(Object id, int code, String message) {
        return Response.ok(Map.of("jsonrpc", "2.0", "id", id, "error", Map.of("code", code, "message", message)))
                .build();
    }

    @GET
    public Response stream() {
        return Response.status(405).build();
    }

    @DELETE
    public Response end() {
        return Response.noContent().build();
    }
}
