package com.beatgame.track.provider.genre;

import com.beatgame.track.Genre;
import org.springframework.stereotype.Component;

@Component("deezerGenreMapper")
public class DeezerGenreMapper implements GenreMapper {

    @Override
    public String map(Genre genre) {
        return switch (genre) {
            case POP        -> "132";
            case ROCK       -> "152";
            case HIP_HOP    -> "116";
            case ELECTRONIC -> "106";
            case RNB        -> "165";
            case JAZZ       -> "129";
            case CLASSICAL  -> "98";
            case METAL      -> "155";
            case COUNTRY    -> "84";
            case LATIN      -> "197";
            case UKRAINIAN  -> throw new UnsupportedOperationException("UKRAINIAN uses artist-based fetch, not genre chart");
        };
    }
}
