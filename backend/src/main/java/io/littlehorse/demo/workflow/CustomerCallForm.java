package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.task.LHUserTaskForm;
import io.littlehorse.sdk.usertask.annotations.UserTaskField;

@LHUserTaskForm(CustomerCallForm.NAME)
public class CustomerCallForm {
    public static final String NAME = "customer-call";

    @UserTaskField(
            displayName = "Customer accepted the offer?",
            description = "Record the customer's decision after taking the call.")
    public boolean accepted;

    @UserTaskField(
            displayName = "Call note",
            description = "Optional context from the customer service agent.",
            required = false)
    public String note;
}
