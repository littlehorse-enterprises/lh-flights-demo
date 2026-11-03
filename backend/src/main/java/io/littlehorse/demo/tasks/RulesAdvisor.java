package io.littlehorse.demo.tasks;

import io.littlehorse.demo.model.*;
import io.littlehorse.quarkus.task.LHTask;
import io.littlehorse.sdk.worker.LHTaskMethod;

@LHTask
public class RulesAdvisor {
    // In model mode this TaskDef is still registered, but its backend worker is disabled.
    @LHTaskMethod("rebooking-advisor")
    public Recommendation advise(AdviceRequest request) {
        return RebookingTasks.deterministic(request);
    }
}
