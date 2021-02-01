package de.dhbw.mannheim.cwb.transit.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Rothenbach
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Trip {
    @JsonProperty("LegList")
    private LegList leglist;

    @JsonProperty("LegList")
    public LegList getLeglist() {
        return leglist;
    }

    @JsonProperty("LegList")
    public void setLeglist(LegList leglist) {
        this.leglist = leglist;
    }
}
