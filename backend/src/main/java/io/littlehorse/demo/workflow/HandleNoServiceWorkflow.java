package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.wfsdk.SpawnedChildWf;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import java.util.Map;

@LHWorkflow(HandleNoServiceWorkflow.NAME)
public class HandleNoServiceWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "handle-no-service";

    @Override
    public void define(WorkflowThread wf) {
        WfRunVariable eventId = wf.declareStr("event-id").required().searchable();
        WfRunVariable customerId = wf.declareStr("customer-id").required().searchable();
        WfRunVariable truckId = wf.declareStr("truck-id").required().searchable();
        WfRunVariable municipalityCode =
                wf.declareStr("municipality-code").required().searchable();
        WfRunVariable serviceStopId =
                wf.declareStr("service-stop-id").required().searchable();
        WfRunVariable address = wf.declareStr("address").required();
        WfRunVariable customerAccepted = wf.declareBool("customer-accepted").required();
        WfRunVariable expectTimeout = wf.declareBool("expect-timeout").required();
        WfRunVariable price = wf.declareDouble("price").required();

        wf.execute("record-exception", eventId, customerId, truckId, "Scheduled service not completed");
        SpawnedChildWf contact = wf.runWf(
                ContactCustomerWorkflow.NAME,
                Map.of(
                        "event-id",
                        wf.format("{0}-offer", eventId),
                        "customer-id",
                        customerId,
                        "message",
                        "Paid next-day corrective pickup offered",
                        "accepted",
                        customerAccepted,
                        "wait-for-response",
                        true));
        WfRunVariable accepted = wf.declareBool("accepted");
        accepted.assign(wf.waitForChildWf(contact));
        wf.doIf(accepted.isEqualTo(true), body -> {
            SpawnedChildWf invoice = body.runWf(
                    SendInvoiceWorkflow.NAME,
                    Map.of(
                            "event-id", wf.format("{0}-payment", eventId),
                            "customer-id", customerId,
                            "municipality-code", municipalityCode,
                            "amount", price,
                            "kind", "corrective-pickup"));
            body.waitForChildWf(invoice);
            SpawnedChildWf dispatch = body.runWf(
                    DispatchTruckWorkflow.NAME,
                    Map.of(
                            "event-id",
                            eventId,
                            "customer-id",
                            customerId,
                            "truck-id",
                            truckId,
                            "service-stop-id",
                            serviceStopId,
                            "address",
                            address,
                            "municipality-code",
                            municipalityCode,
                            "dispatch-date",
                            "next-day",
                            "expect-timeout",
                            expectTimeout));
            body.waitForChildWf(dispatch);
        });
    }
}
