package io.littlehorse.demo.model;

import java.time.Instant;
import java.util.List;

public record UserTaskView(
        String wfRunId,
        String userTaskGuid,
        String formName,
        String status,
        String userId,
        String userGroup,
        String notes,
        Instant scheduledTime,
        List<FieldView> fields) {
    public record FieldView(String name, String type, String displayName, String description, boolean required) {}
}
