package io.littlehorse.demo;

import static org.junit.jupiter.api.Assertions.*;

import io.littlehorse.demo.model.*;
import io.littlehorse.demo.workflow.*;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.wfsdk.Workflow;
import org.junit.jupiter.api.Test;

class WorkflowTest {
    @Test
    void typedWorkflowGraphsCompile() {
        assertNotNull(
                Workflow.newWorkflow("booking", new BookingWorkflow()::define).compileWorkflow());
        assertNotNull(Workflow.newWorkflow("reserve-itinerary", new ReservationWorkflow()::define)
                .compileWorkflow());
        assertNotNull(Workflow.newWorkflow("reaccommodate-customer", new ReaccommodateWorkflow()::define)
                .compileWorkflow());
        assertNotNull(Workflow.newWorkflow("cancel-booking", new CancelBookingWorkflow()::define)
                .compileWorkflow());
    }

    @Test
    void structsAndNativeArraysRoundTrip() {
        BookedTrip trip = new BookedTrip("abc", "p001", 1, "CONFIRMED", new FlightLeg[] {
            AirlineTopologyTest.leg("LH101", "RNO", "DEN", "15:15:00Z", "17:00:00Z")
        });
        VariableValue encoded = LHLibUtil.objToVarVal(trip);
        assertTrue(encoded.hasStruct());
        assertEquals("abc", ((BookedTrip) LHLibUtil.varValToObj(encoded, BookedTrip.class)).getTripId());
        assertTrue(encoded.getStruct()
                .getStruct()
                .getFieldsOrThrow("legs")
                .getValue()
                .hasArray());
    }
}
