package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.provider.genre.ItunesGenreMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.github.resilience4j.retry.annotation.Retry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component("itunesProvider")
public class ItunesProvider implements TrackProvider {

    private final RestClient client;
    private final ItunesGenreMapper genreMapper;

    public ItunesProvider(@Qualifier("itunesClient") RestClient client, ItunesGenreMapper genreMapper) {
        this.client = client;
        this.genreMapper = genreMapper;
    }

    @Override
    @Retry(name = "itunes")
    public List<Track> fetchByGenre(Genre genre, int limit, int offset) {
        if (genre == Genre.UKRAINIAN) return Collections.emptyList();
        List<String> terms = genreMapper.mapAll(genre);
        String term = terms.get((offset / limit) % terms.size());
        ItunesSearchResponse response = fetchSearch(
            "/search?term={genre}&media=music&entity=song&attribute=genreIndex&limit={limit}&offset=0",
            term, limit);
        if (response == null || response.results() == null) return Collections.emptyList();
        return response.results().stream()
            .filter(t -> isValidPreview(t.previewUrl()))
            .map(t -> toTrack(t, genre, null))
            .toList();
    }

    @Override
    @Retry(name = "itunes")
    public List<Track> fetchByDecade(Decade decade, int limit, int offset) {
        ItunesSearchResponse response = fetchSearch(
            "/search?term=music&media=music&entity=song&limit={limit}&offset={offset}",
            limit, offset);
        if (response == null || response.results() == null) return Collections.emptyList();
        return response.results().stream()
            .filter(t -> isValidPreview(t.previewUrl()))
            .filter(t -> isInDecade(t.releaseDate(), decade))
            .map(t -> toTrack(t, null, decade))
            .toList();
    }

    private ItunesSearchResponse fetchSearch(String uriTemplate, Object... uriVars) {
        return client.get()
            .uri(uriTemplate, uriVars)
            .retrieve()
            .body(ItunesSearchResponse.class);
    }

    private boolean isValidPreview(String url) {
        return url != null && url.startsWith("https://");
    }

    private boolean isInDecade(String releaseDate, Decade decade) {
        if (releaseDate == null || releaseDate.length() < 4) return false;
        try {
            int year = Integer.parseInt(releaseDate.substring(0, 4));
            return year >= decade.from && year <= decade.to;
        } catch (Exception e) {
            return false;
        }
    }

    private Track toTrack(ItunesTrackDto dto, Genre genre, Decade decade) {
        Track track = new Track();
        track.setTitle(dto.trackName());
        track.setArtist(dto.artistName());
        track.setGenre(genre);
        track.setPreviewUrl(dto.previewUrl());
        track.setCoverUrl(dto.artworkUrl100());
        track.setProvider("ITUNES");
        track.setExternalId(String.valueOf(dto.trackId()));
        if (decade != null) {
            track.setDecade((short) decade.from);
        } else if (dto.releaseDate() != null && dto.releaseDate().length() >= 4) {
            try {
                int year = Integer.parseInt(dto.releaseDate().substring(0, 4));
                Decade d = Decade.fromYear(year);
                if (d != null) track.setDecade((short) d.from);
            } catch (Exception ignored) {}
        }
        return track;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItunesSearchResponse(int resultCount, List<ItunesTrackDto> results) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ItunesTrackDto(
        long trackId,
        String trackName,
        String artistName,
        String artworkUrl100,
        String previewUrl,
        String releaseDate,
        String primaryGenreName) {}
}
