// TmdbVideosDto.java — mirrors TMDB /tv/{id}/videos payload
package com.monprojet.series.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbVideosDto(
        List<Video> results
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Video(
            String id,
            String key,
            String name,
            String site,
            String type,
            @JsonProperty("official") Boolean official,
            @JsonProperty("iso_639_1") String langue
    ) {}
}