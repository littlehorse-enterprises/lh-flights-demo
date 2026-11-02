package io.littlehorse.demo.api;

import io.littlehorse.demo.kafka.KafkaPublisher;
import io.littlehorse.demo.store.DemoStore;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.Map;
import java.util.Set;

@Path("/webhooks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class WebhookResource {
    private static final Set<String> WF_TOPICS = Set.of("pickups", "overfilled-cans", "no-service-events");
    private static final String CUSTOMER_RESPONSE_TOPIC = "customer-responses";
    private final KafkaPublisher publisher;
    private final DemoStore store;

    public WebhookResource(KafkaPublisher publisher, DemoStore store) {
        this.publisher = publisher;
        this.store = store;
    }

    @POST
    @Path("/{type}")
    public Response receive(@PathParam("type") String type, Map<String, Object> payload) {
        if ("truck-locations".equals(type)) {
            publisher.publishLocation(payload);
        } else if ("service-exceptions".equals(type)) {
            publisher.publish(type, KafkaPublisher.required(payload, "serviceStopId"), payload);
        } else if (WF_TOPICS.contains(type)) {
            publisher.publish(type, KafkaPublisher.required(payload, "event-id"), payload);
        } else if (CUSTOMER_RESPONSE_TOPIC.equals(type)) {
            publisher.publish(type, KafkaPublisher.required(payload, "eventId"), payload);
        } else if ("simulation-started".equals(type)) {
            store.simulationStarted();
        } else if ("simulation-stopped".equals(type)) {
            store.simulationStopped();
        } else {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "Unknown webhook type"))
                    .build();
        }
        return Response.accepted(Map.of("accepted", true, "type", type)).build();
    }
}
