package io.littlehorse.demo.workflow;

import io.littlehorse.demo.model.*;
import io.littlehorse.quarkus.workflow.*;
import io.littlehorse.sdk.wfsdk.*;
import java.util.Map;

@LHWorkflow(BookingWorkflow.NAME)
public class BookingWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "booking";

    public void define(WorkflowThread wf) {
        var tripId = wf.declareStr("tripId").required().searchable();
        var passengerId = wf.declareStr("passengerId").required().searchable();
        var flights = wf.declareArray("flightNumbers", String.class).required();
        var date = wf.declareStr("date").required();
        var request = wf.declareStruct("request", ReservationRequest.class);
        request.assign(wf.execute("prepare-booking", tripId, passengerId, flights, date));
        var child = wf.runWf(ReservationWorkflow.NAME, Map.of("request", request));
        var trip = wf.declareStruct("trip", BookedTrip.class);
        trip.assign(wf.waitForChildWf(child));
        wf.complete(trip);
    }
}
