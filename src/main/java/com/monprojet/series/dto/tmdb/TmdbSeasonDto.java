// TmdbSeasonDto.java
package com.monprojet.series.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonDto(
        @JsonProperty("season_number") Integer numeroSaison,
        @JsonProperty("episode_count") Integer nombreEpisodes,
        @JsonProperty("air_date") String dateDiffusion,
        String name
) {}