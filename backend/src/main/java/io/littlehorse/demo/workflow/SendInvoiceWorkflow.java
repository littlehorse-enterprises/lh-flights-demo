package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;

@LHWorkflow(SendInvoiceWorkflow.NAME)
public class SendInvoiceWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "send-invoice";

    @Override
    public void define(WorkflowThread wf) {
        WfRunVariable eventId = wf.declareStr("event-id").required();
        WfRunVariable customerId = wf.declareStr("customer-id").required();
        WfRunVariable municipalityCode = wf.declareStr("municipality-code").required();
        WfRunVariable amount = wf.declareDouble("amount").required();
        WfRunVariable kind = wf.declareStr("kind").required();

        wf.complete(wf.execute("capture-purchase", eventId, customerId, amount, kind));
    }
}
