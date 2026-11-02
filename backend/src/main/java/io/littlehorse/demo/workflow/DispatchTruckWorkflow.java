package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.common.proto.Comparator;
import io.littlehorse.sdk.common.proto.LHErrorType;
import io.littlehorse.sdk.wfsdk.ExternalEventNodeOutput;
import io.littlehorse.sdk.wfsdk.UserTaskOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;

@LHWorkflow(DispatchTruckWorkflow.NAME)
public class DispatchTruckWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "dispatch-truck";

    private final int pickupTimeoutSeconds;

    public DispatchTruckWorkflow() {
        this.pickupTimeoutSeconds = 20;
    }

    @Override
    public void define(WorkflowThread wf) {
        WfRunVariable eventId = wf.declareStr("event-id").required();
        WfRunVariable customerId = wf.declareStr("customer-id").required();
        WfRunVariable truckId = wf.declareStr("truck-id").required();
        WfRunVariable serviceStopId =
                wf.declareStr("service-stop-id").required().searchable();
        WfRunVariable address = wf.declareStr("address").required();
        WfRunVariable municipalityCode = wf.declareStr("municipality-code").required();
        WfRunVariable dispatchDate = wf.declareStr("dispatch-date").required();
        WfRunVariable expectTimeout = wf.declareBool("expect-timeout").required();

        wf.execute("create-dispatch-next-day", serviceStopId, customerId, address, municipalityCode, dispatchDate);
        ExternalEventNodeOutput pickup = wf.waitForEvent("service-completed")
                .withCorrelationId(serviceStopId)
                .registeredAs(ServiceCompletion.class)
                .timeout(pickupTimeoutSeconds);
        wf.handleError(pickup, LHErrorType.TIMEOUT, handler -> {
            UserTaskOutput recovery = handler.assignUserTask(OperatorRecoveryForm.NAME, null, "dispatch-operations")
                    .withNotes(handler.format(
                            "Pickup confirmation timed out for customer {0}, stop {1}, at {2}. Review and dispatch a replacement.",
                            customerId, serviceStopId, address));
            WfRunVariable approved = handler.declareBool("recovery-approved");
            approved.assign(recovery.jsonPath("$.approved"));
            handler.doIf(handler.condition(approved, Comparator.EQUALS, true), body -> {
                        body.execute("notify-service-status", eventId, customerId, "Replacement pickup dispatched");
                        body.waitForEvent("service-completed")
                                .withCorrelationId(serviceStopId)
                                .registeredAs(ServiceCompletion.class)
                                .timeout(pickupTimeoutSeconds * 2);
                    })
                    .doElse(body -> body.fail("recovery-declined", "Operator declined replacement service"));
        });
        wf.execute("notify-service-status", eventId, customerId, "Corrective pickup completed");
        wf.doIf(
                expectTimeout.isEqualTo(true),
                body -> body.execute("record-service-recovery", eventId, customerId, truckId));
        wf.execute("record-customer-ledger", eventId, customerId, "Corrective pickup completed and verified");
    }
}
