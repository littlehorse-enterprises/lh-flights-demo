package io.littlehorse.demo.workflow;

import io.littlehorse.quarkus.task.LHUserTaskForm;
import io.littlehorse.sdk.usertask.annotations.UserTaskField;

@LHUserTaskForm(OperatorRecoveryForm.NAME)
public class OperatorRecoveryForm {
    public static final String NAME = "recover-failed-service";

    @UserTaskField(
            displayName = "Dispatch replacement pickup?",
            description = "Approve to dispatch a replacement vehicle and continue tracking service.")
    public boolean approved;

    @UserTaskField(
            displayName = "Operator note",
            description = "Optional context for the recovery audit trail.",
            required = false)
    public String note;
}
