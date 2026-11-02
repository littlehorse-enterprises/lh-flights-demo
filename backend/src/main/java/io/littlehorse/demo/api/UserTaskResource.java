package io.littlehorse.demo.api;

import io.littlehorse.demo.model.UserTaskView;
import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.AssignUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.SearchUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskDef;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.UserTaskRunStatus;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Path("/api/user-tasks")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserTaskResource {
    private final LittleHorseBlockingStub client;

    public UserTaskResource(LittleHorseBlockingStub client) {
        this.client = client;
    }

    @GET
    public List<UserTaskView> search() {
        ArrayList<UserTaskView> results = new ArrayList<>();
        results.addAll(search(UserTaskRunStatus.UNASSIGNED));
        results.addAll(search(UserTaskRunStatus.ASSIGNED));
        results.sort(Comparator.comparing(UserTaskView::scheduledTime).reversed());
        return results;
    }

    @POST
    @Path("/{wfRunId}/{userTaskGuid}/claim")
    public UserTaskView claim(
            @PathParam("wfRunId") String wfRunId,
            @PathParam("userTaskGuid") String userTaskGuid,
            ClaimRequest request) {
        UserTaskRunId id = resolveId(wfRunId, userTaskGuid);
        client.assignUserTaskRun(AssignUserTaskRunRequest.newBuilder()
                .setUserTaskRunId(id)
                .setUserId(request.userId())
                .setOverrideClaim(false)
                .build());
        return view(client.getUserTaskRun(id));
    }

    @POST
    @Path("/{wfRunId}/{userTaskGuid}/complete")
    public void complete(
            @PathParam("wfRunId") String wfRunId,
            @PathParam("userTaskGuid") String userTaskGuid,
            CompleteRequest request) {
        UserTaskRunId id = resolveId(wfRunId, userTaskGuid);
        UserTaskDef definition = client.getUserTaskDef(client.getUserTaskRun(id).getUserTaskDefId());
        CompleteUserTaskRunRequest.Builder completion =
                CompleteUserTaskRunRequest.newBuilder().setUserTaskRunId(id).setUserId(request.userId());
        if (hasField(definition, "approved")) {
            completion.putResults("approved", LHLibUtil.objToVarVal(required(request.approved(), "approved")));
        }
        if (hasField(definition, "accepted")) {
            completion.putResults("accepted", LHLibUtil.objToVarVal(required(request.accepted(), "accepted")));
        }
        if (hasField(definition, "note")) {
            completion.putResults("note", LHLibUtil.objToVarVal(request.note() == null ? "" : request.note()));
        }
        client.completeUserTaskRun(completion.build());
    }

    private List<UserTaskView> search(UserTaskRunStatus status) {
        return client
                .searchUserTaskRun(SearchUserTaskRunRequest.newBuilder()
                        .setStatus(status)
                        .setLimit(100)
                        .build())
                .getResultsList()
                .stream()
                .map(client::getUserTaskRun)
                .map(this::view)
                .toList();
    }

    private UserTaskView view(UserTaskRun task) {
        UserTaskDef definition = client.getUserTaskDef(task.getUserTaskDefId());
        return new UserTaskView(
                task.getId().getWfRunId().getId(),
                task.getId().getUserTaskGuid(),
                task.getUserTaskDefId().getName(),
                task.getStatus().name(),
                task.hasUserId() ? task.getUserId() : null,
                task.hasUserGroup() ? task.getUserGroup() : null,
                task.hasNotes() ? task.getNotes() : "",
                Instant.ofEpochSecond(
                        task.getScheduledTime().getSeconds(),
                        task.getScheduledTime().getNanos()),
                definition.getFieldsList().stream()
                        .map(field -> new UserTaskView.FieldView(
                                field.getName(),
                                field.getType().name(),
                                field.getDisplayName(),
                                field.hasDescription() ? field.getDescription() : "",
                                field.getRequired()))
                        .toList());
    }

    private UserTaskRunId resolveId(String wfRunId, String userTaskGuid) {
        return java.util.stream.Stream.concat(
                        searchIds(UserTaskRunStatus.UNASSIGNED).stream(),
                        searchIds(UserTaskRunStatus.ASSIGNED).stream())
                .filter(candidate -> candidate.getWfRunId().getId().equals(wfRunId))
                .filter(candidate -> candidate.getUserTaskGuid().equals(userTaskGuid))
                .findFirst()
                .orElseThrow(() -> new jakarta.ws.rs.NotFoundException("User task not found"));
    }

    private List<UserTaskRunId> searchIds(UserTaskRunStatus status) {
        return client.searchUserTaskRun(SearchUserTaskRunRequest.newBuilder()
                        .setStatus(status)
                        .setLimit(100)
                        .build())
                .getResultsList();
    }

    private boolean hasField(UserTaskDef definition, String name) {
        return definition.getFieldsList().stream()
                .anyMatch(field -> field.getName().equals(name));
    }

    private boolean required(Boolean value, String field) {
        if (value == null) throw new jakarta.ws.rs.BadRequestException("Missing result " + field);
        return value;
    }

    public record ClaimRequest(String userId) {}

    public record CompleteRequest(String userId, Boolean approved, Boolean accepted, String note) {}
}
