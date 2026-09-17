// TmdbSerieDetailDto.java
package com.monprojet.series.dto.tmdb;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSerieDetailDto(
        Long id,
        String name,
        String overview,
        @JsonProperty("first_air_date") String firstAirDate,
        @JsonProperty("last_air_date") String lastAirDate,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("number_of_seasons") Integer numberOfSeasons,
        @JsonProperty("number_of_episodes") Integer numberOfEpisodes,
        String status, // "Running", "Ended", "Canceled", "In Production"...
        List<TmdbSeasonDto> seasons
) {}