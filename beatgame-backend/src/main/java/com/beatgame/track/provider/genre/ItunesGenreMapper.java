package com.beatgame.track.provider.genre;

import com.beatgame.track.Genre;
import org.springframework.stereotype.Component;

import java.util.List;

@Component("itunesGenreMapper")
public class ItunesGenreMapper implements GenreMapper {

    @Override
    public String map(Genre genre) {
        return mapAll(genre).get(0);
    }

    public List<String> mapAll(Genre genre) {
        return switch (genre) {
            case POP        -> List.of("Pop", "pop hits", "pop music", "top pop", "pop songs");
            case ROCK       -> List.of("Rock", "classic rock", "alternative rock", "rock music", "indie rock");
            case HIP_HOP    -> List.of("Hip-Hop/Rap", "rap", "hip hop", "trap", "rap music");
            case ELECTRONIC -> List.of("Electronic", "EDM", "dance music", "house music", "techno");
            case RNB        -> List.of("R&B/Soul", "R&B", "soul music", "rhythm and blues", "neo soul");
            case JAZZ       -> List.of("Jazz", "smooth jazz", "jazz music", "jazz piano", "jazz fusion");
            case CLASSICAL  -> List.of("Classical", "classical music", "symphony", "piano", "opera");
            case METAL      -> List.of("Metal", "heavy metal", "thrash metal", "metal music", "hard rock");
            case COUNTRY    -> List.of("Country", "country music", "country hits", "pop country", "bluegrass");
            case LATIN      -> List.of("Latino", "reggaeton", "Latin pop", "salsa", "bachata");
            case UKRAINIAN  -> throw new UnsupportedOperationException("UKRAINIAN is not supported on iTunes");
        };
    }
}
