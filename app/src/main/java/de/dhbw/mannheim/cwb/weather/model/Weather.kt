package de.dhbw.mannheim.cwb.weather.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class Weather @JsonCreator constructor(
    @JsonProperty("id") val id: Int, @JsonProperty("main") val main: String,
    @JsonProperty("description") val description: String, @JsonProperty("icon") val icon: String
)