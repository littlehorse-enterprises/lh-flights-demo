package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("rebooking-recommendation")
public class Recommendation {
    private String optionId;
    private String explanation;
    private String source;

    public Recommendation() {}

    public Recommendation(String optionId, String explanation, String source) {
        this.optionId = optionId;
        this.explanation = explanation;
        this.source = source;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public String getExplanation() {
        return explanation;
    }

    public void setExplanation(String explanation) {
        this.explanation = explanation;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}
