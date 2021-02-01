package de.dhbw.mannheim.cwb.transit.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Rothenbach
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LegList {

    @JsonProperty("Leg")
    private Leg[] legs;

    @JsonProperty("Leg")
    public Leg[] getLegs() {
        return legs;
    }

    @JsonProperty("Leg")
    public void setLegs(Leg[] legs) {
        this.legs = legs;
    }
}
