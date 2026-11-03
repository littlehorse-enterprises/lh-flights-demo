package io.littlehorse.demo.workflow;

import io.littlehorse.demo.model.*;
import io.littlehorse.quarkus.workflow.*;
import io.littlehorse.sdk.common.proto.LHErrorType;
import io.littlehorse.sdk.wfsdk.*;
import java.util.Map;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@LHWorkflow(ReaccommodateWorkflow.NAME)
public class ReaccommodateWorkflow implements LHWorkflowDefinition {
    public static final String NAME = "reaccommodate-customer";

    @ConfigProperty(name = "airline.response-seconds")
    int responseSeconds;

    public void define(WorkflowThread wf) {
        var passenger = wf.declareStr("passengerId").required().searchable();
        var trip = wf.declareStr("tripId").required().searchable();
        var advice = wf.declareStruct("advice", AdviceRequest.class);
        advice.assign(wf.execute("create-advice", passenger, trip).withRetries(3));
        var recommendation = wf.declareStruct("recommendation", Recommendation.class);
        var agent = wf.execute("rebooking-advisor", advice).timeout(60).withRetries(0);
        recommendation.assign(agent);
        wf.handleError(agent, handler -> recommendation.assign(handler.execute("fallback-advice", advice)));
        var proposal = wf.declareStruct("proposal", Proposal.class);
        proposal.assign(wf.execute("create-proposal", advice, recommendation));
        wf.doIf(proposal.get("status").isEqualTo("PENDING"), body -> {
                    var timedOut = body.declareBool("timedOut").withDefault(false);
                    var decision = body.declareStruct("decision", PassengerDecision.class);
                    var wait = body.waitForEvent("passenger-response")
                            .withCorrelationId(proposal.get("id"))
                            .registeredAs(PassengerDecision.class)
                            .timeout(responseSeconds);
                    decision.assign(wait);
                    body.handleError(wait, LHErrorType.TIMEOUT, handler -> {
                        timedOut.assign(true);
                        var outcome = handler.execute("finish-rebooking", proposal.get("id"), "TIMED_OUT");
                        handler.throwEvent("rebooking-finished", outcome).registeredAs(RebookingOutcome.class);
                    });
                    // Failed node output mutations are skipped; the handler records the timeout.
                    body.doIf(timedOut.isEqualTo(false), responded -> {
                        responded.throwEvent("passenger-decided", decision).registeredAs(PassengerDecision.class);
                        responded
                                .doIf(decision.get("accepted").isEqualTo(true), accepted -> {
                                    var request = accepted.declareStruct("change", ReservationRequest.class);
                                    request.assign(accepted.execute("prepare-change", proposal, decision));
                                    var child = accepted.runWf(ReservationWorkflow.NAME, Map.of("request", request));
                                    var booked = accepted.declareStruct("rebooked", BookedTrip.class);
                                    booked.assign(accepted.waitForChildWf(child));
                                    accepted.doIf(booked.get("status").isEqualTo("CONFIRMED"), success -> {
                                                var outcome = success.execute(
                                                        "finish-rebooking", proposal.get("id"), "REBOOKED");
                                                success.throwEvent("rebooking-finished", outcome)
                                                        .registeredAs(RebookingOutcome.class);
                                            })
                                            .doElse(unavailable -> {
                                                var outcome = unavailable.execute(
                                                        "finish-rebooking", proposal.get("id"), "UNAVAILABLE");
                                                unavailable
                                                        .throwEvent("rebooking-finished", outcome)
                                                        .registeredAs(RebookingOutcome.class);
                                            });
                                })
                                .doElse(declined -> {
                                    var outcome = declined.execute("finish-rebooking", proposal.get("id"), "DECLINED");
                                    declined.throwEvent("rebooking-finished", outcome)
                                            .registeredAs(RebookingOutcome.class);
                                });
                    });
                })
                .doElse(closed -> {
                    var outcome = closed.execute("finish-rebooking", proposal.get("id"), proposal.get("status"));
                    closed.throwEvent("rebooking-finished", outcome).registeredAs(RebookingOutcome.class);
                });
    }
}
