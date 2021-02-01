package de.dhbw.mannheim.cwb.transit.pojo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author Lukas Rothenbach
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class Leg {

    @JsonProperty("Origin")
    private Stop origin;

    @JsonProperty("Destination")
    private Stop destination;

    @JsonProperty("name")
    private String name;

    @JsonProperty("category")
    private String category;

    @JsonProperty("number")
    private String number;

    @JsonProperty("type")
    private String type;

    @JsonProperty("Origin")
    public Stop getOrigin() {
        return origin;
    }

    @JsonProperty("Origin")
    public void setOrigin(Stop origin) {
        this.origin = origin;
    }

    @JsonProperty("Destination")
    public Stop getDestination() {
        return destination;
    }

    @JsonProperty("Destination")
    public void setDestination(Stop destination) {
        this.destination = destination;
    }

    @JsonProperty("name")
    public String getName() {
        return name;
    }

    @JsonProperty("name")
    public void setName(String name) {
        this.name = name;
    }

    @JsonProperty("category")
    public String getCategory() {
        return category;
    }

    @JsonProperty("category")
    public void setCategory(String category) {
        this.category = category;
    }

    @JsonProperty("number")
    public String getNumber() {
        return category;
    }

    @JsonProperty("number")
    public void setNumber(String category) {
        this.category = category;
    }

    @JsonProperty("type")
    public String getType() {
        return type;
    }

    @JsonProperty("type")
    public void setType(String type) {
        this.type = type;
    }
}
