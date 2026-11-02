package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;

@LHWorkflow(RecordPickupWorkflow.NAME)
public class RecordPickupWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "record-pickup";

    @Override
    public void define(WorkflowThread wf) {
        WfRunVariable eventId = wf.declareStr("event-id").required().searchable();
        WfRunVariable customerId = wf.declareStr("customer-id").required().searchable();
        WfRunVariable truckId = wf.declareStr("truck-id").required().searchable();
        WfRunVariable serviceStopId =
                wf.declareStr("service-stop-id").required().searchable();
        WfRunVariable municipalityCode =
                wf.declareStr("municipality-code").required().searchable();

        wf.execute("record-pickup-ledger", eventId, customerId, truckId, serviceStopId, municipalityCode);
    }
}
