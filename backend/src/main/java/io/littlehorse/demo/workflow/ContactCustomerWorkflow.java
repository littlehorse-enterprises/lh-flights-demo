package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.workflow.LHWorkflow;
import io.littlehorse.quarkus.workflow.LHWorkflowDefinition;
import io.littlehorse.sdk.wfsdk.ExternalEventNodeOutput;
import io.littlehorse.sdk.wfsdk.UserTaskOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.WorkflowThread;

@LHWorkflow(ContactCustomerWorkflow.NAME)
public class ContactCustomerWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "contact-customer";

    private WfRunVariable eventId;
    private WfRunVariable customerId;
    private WfRunVariable message;
    private WfRunVariable accepted;
    private WfRunVariable contactMethod;
    private WfRunVariable waitForResponse;
    private WfRunVariable response;

    @Override
    public void define(WorkflowThread wf) {
        eventId = wf.declareStr("event-id").required();
        customerId = wf.declareStr("customer-id").required();
        message = wf.declareStr("message").required();
        accepted = wf.declareBool("accepted").required();
        contactMethod = wf.declareStr("contact-method").asPublic();
        waitForResponse = wf.declareBool("wait-for-response").required();
        response = wf.declareBool("response");

        // The lookup task represents a read from the customer-preferences database.
        contactMethod.assign(wf.execute("lookup-contact-method", customerId));
        wf.doIf(contactMethod.isEqualTo("PHONE"), this::phone)
                .doElseIf(contactMethod.isEqualTo("TEXT"), this::text)
                .doElse(this::push);

        wf.doIf(waitForResponse, handler -> {
            handler.execute("record-customer-response", eventId, customerId, contactMethod, response);
        });

        wf.complete(response);
    }

    private void phone(WorkflowThread thread) {
        // This user task is always scheduled: it is the work order that tells the contact center to call.
        UserTaskOutput call = thread.assignUserTask(CustomerCallForm.NAME, null, "customer-service")
                .withNotes(thread.format(
                        "Contact {0}: call customer {1} about: {2}. Record the customer's decision after the call.",
                        eventId, customerId, message));
        response.assign(call.jsonPath("$.accepted"));
        response.assign(thread.execute("contact-center-call", eventId, customerId, message, response));
    }

    private void text(WorkflowThread thread) {
        thread.execute("send-text-message", eventId, customerId, message, accepted);
        thread.doIf(waitForResponse, this::waitForTextResponse).doElse(this::skipResponse);
    }

    private void push(WorkflowThread thread) {
        thread.execute("send-push-notification", eventId, customerId, message, accepted);
        thread.doIf(waitForResponse, this::waitForPushResponse).doElse(this::skipResponse);
    }

    private void waitForTextResponse(WorkflowThread thread) {
        ExternalEventNodeOutput customerResponse = thread.waitForEvent("customer-responded")
                .withCorrelationId(eventId)
                .registeredAs(CustomerResponse.class);
        response.assign(customerResponse.get("accepted"));
    }

    private void waitForPushResponse(WorkflowThread thread) {
        ExternalEventNodeOutput customerResponse = thread.waitForEvent("customer-responded")
                .withCorrelationId(eventId)
                .registeredAs(CustomerResponse.class);
        response.assign(customerResponse.get("accepted"));
    }

    private void skipResponse(WorkflowThread thread) {
        response.assign(accepted);
    }
}
