package io.littlehorse.demo.model;

import io.littlehorse.sdk.worker.LHStructDef;

@LHStructDef("itinerary")
public class Itinerary {
    private String optionId;
    private FlightLeg[] legs;
    private boolean overnight;

    public Itinerary() {}

    public Itinerary(String optionId, FlightLeg[] legs, boolean overnight) {
        this.optionId = optionId;
        this.legs = legs;
        this.overnight = overnight;
    }

    public String getOptionId() {
        return optionId;
    }

    public void setOptionId(String optionId) {
        this.optionId = optionId;
    }

    public FlightLeg[] getLegs() {
        return legs;
    }

    public void setLegs(FlightLeg[] legs) {
        this.legs = legs;
    }

    public boolean isOvernight() {
        return overnight;
    }

    public void setOvernight(boolean overnight) {
        this.overnight = overnight;
    }
}
