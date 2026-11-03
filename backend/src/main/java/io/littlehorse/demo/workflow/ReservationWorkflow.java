package io.littlehorse.demo.workflow;

import io.littlehorse.demo.model.*;
import io.littlehorse.quarkus.workflow.*;
import io.littlehorse.sdk.wfsdk.*;

@LHWorkflow(ReservationWorkflow.NAME)
public class ReservationWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "reserve-itinerary";

    public void define(WorkflowThread wf) {
        var request = wf.declareStruct("request", ReservationRequest.class).required();
        var trip = wf.declareStruct("trip", BookedTrip.class);
        trip.assign(wf.execute("reserve-itinerary", request).withRetries(3));
        wf.doIf(trip.get("status").isEqualTo("CONFIRMED"), body -> body.throwEvent("trip-booked", trip)
                .registeredAs(BookedTrip.class));
        wf.complete(trip);
    }
}
