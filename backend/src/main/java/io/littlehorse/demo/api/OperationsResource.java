package io.littlehorse.demo.api;

import io.littlehorse.demo.model.ActivityEvent;
import io.littlehorse.demo.model.OutcomeSnapshot;
import io.littlehorse.demo.store.DemoStore;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class OperationsResource {
    private final DemoStore store;

    public OperationsResource(DemoStore store) {
        this.store = store;
    }

    @GET
    @Path("/outcomes")
    public OutcomeSnapshot outcomes() {
        return store.outcomes();
    }

    @GET
    @Path("/activity")
    public List<ActivityEvent> activity() {
        return store.activity();
    }

    @POST
    @Path("/reset")
    public OutcomeSnapshot reset() {
        store.reset();
        return store.outcomes();
    }
}
