package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.wfsdk.SpawnedChildWf;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import java.util.Map;

@LHWorkflow(HandleOverfilledCanWorkflow.NAME)
public class HandleOverfilledCanWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "handle-overfilled-can";

    private WfRunVariable eventId;
    private WfRunVariable customerId;
    private WfRunVariable truckId;
    private WfRunVariable municipalityCode;
    private WfRunVariable serviceStopId;
    private WfRunVariable address;
    private WfRunVariable scenario;
    private WfRunVariable customerAccepted;
    private WfRunVariable tagPrice;
    private WfRunVariable feeAmount;
    private WfRunVariable decision;

    @Override
    public void define(WorkflowThread wf) {
        eventId = wf.declareStr("event-id").required().searchable();
        customerId = wf.declareStr("customer-id").required().searchable();
        truckId = wf.declareStr("truck-id").required().searchable();
        municipalityCode = wf.declareStr("municipality-code").required().searchable();
        serviceStopId = wf.declareStr("service-stop-id").required().searchable();
        address = wf.declareStr("address").required();
        scenario = wf.declareStr("scenario").required().searchable();
        customerAccepted = wf.declareBool("customer-accepted").required();
        tagPrice = wf.declareDouble("tag-price").required();
        feeAmount = wf.declareDouble("fee-amount").required();
        decision = wf.declareStr("decision").asPublic();

        wf.execute("record-exception", eventId, customerId, truckId, "Overfilled container detected");
        decision.assign(wf.execute("consult-local-rules", customerId, municipalityCode, scenario));
        wf.doIf(decision.isEqualTo("WARN"), this::warning)
                .doElseIf(decision.isEqualTo("OFFER_TAGS"), this::purchaseTags)
                .doElse(this::assessFee);
        wf.execute("record-customer-ledger", eventId, customerId, wf.format("Overfill decision: {0}", decision));
    }

    private void warning(WorkflowThread thread) {
        thread.runWf(
                ContactCustomerWorkflow.NAME,
                Map.of(
                        "event-id",
                        thread.format("{0}-warning", eventId),
                        "customer-id",
                        customerId,
                        "message",
                        "Service policy warning delivered",
                        "accepted",
                        true,
                        "wait-for-response",
                        false));
    }

    private void purchaseTags(WorkflowThread thread) {
        SpawnedChildWf contact = thread.runWf(
                ContactCustomerWorkflow.NAME,
                Map.of(
                        "event-id",
                        thread.format("{0}-tag-offer", eventId),
                        "customer-id",
                        customerId,
                        "message",
                        "Additional service tags offered",
                        "accepted",
                        customerAccepted,
                        "wait-for-response",
                        true));
        WfRunVariable contactAccepted = thread.declareBool("tag-contact-accepted");
        contactAccepted.assign(thread.waitForChildWf(contact));
        thread.doIf(contactAccepted.isEqualTo(true), this::completeTagPurchase);
    }

    private void completeTagPurchase(WorkflowThread thread) {
        thread.runWf(
                SendInvoiceWorkflow.NAME,
                Map.of(
                        "event-id",
                        thread.format("{0}-tag-purchase", eventId),
                        "customer-id",
                        customerId,
                        "municipality-code",
                        municipalityCode,
                        "amount",
                        tagPrice,
                        "kind",
                        "tags"));

        SpawnedChildWf dispatch = thread.runWf(
                DispatchTruckWorkflow.NAME,
                Map.of(
                        "event-id",
                        thread.format("{0}-tag-dispatch", eventId),
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
                        false));
        thread.waitForChildWf(dispatch);
    }

    private void assessFee(WorkflowThread thread) {
        SpawnedChildWf invoice = thread.runWf(
                SendInvoiceWorkflow.NAME,
                Map.of(
                        "event-id",
                        thread.format("{0}-fee", eventId),
                        "customer-id",
                        customerId,
                        "municipality-code",
                        municipalityCode,
                        "amount",
                        feeAmount,
                        "kind",
                        "overfill-fee"));
        thread.waitForChildWf(invoice);
    }
}
