package io.littlehorse.demo.workflow;

import io.littlehorse.demo.model.BookedTrip;
import io.littlehorse.quarkus.workflow.*;
import io.littlehorse.sdk.wfsdk.*;

@LHWorkflow("cancel-booking")
public class CancelBookingWorkflow implements LHWorkflowDefinition {
    public void define(WorkflowThread wf) {
        var id = wf.declareStr("tripId").required();
        var trip = wf.execute("cancel-booking", id).withRetries(3);
        wf.throwEvent("trip-booked", trip).registeredAs(BookedTrip.class);
        wf.complete(trip);
    }
}
