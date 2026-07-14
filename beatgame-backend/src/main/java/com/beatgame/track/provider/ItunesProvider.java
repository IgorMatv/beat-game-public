package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.provider.genre.ItunesGenreMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component("itunesProvider")
public class ItunesProvider implements TrackProvider {

    private static final Logger log = LoggerFactory.getLogger(ItunesProvider.class);
    private static final int DELAY_MS = 1000;
    private static final int MAX_RETRIES = 3;

    private final RestClient client;
    private final ItunesGenreMapper genreMapper;

    public ItunesProvider(@Qualifier("itunesClient") RestClient client, ItunesGenreMapper genreMapper) {
        this.client = client;
        this.genreMapper = genreMapper;
    }

    @Override
    public List<Track> fetchByGenre(Genre genre, int limit, int offset) {
        if (genre == Genre.UKRAINIAN) return Collections.emptyList();
        List<String> terms = genreMapper.mapAll(genre);
        String term = terms.get((offset / limit) % terms.size());
        ItunesSearchResponse response = fetchWithRetry(
            "/search?term={genre}&media=music&entity=song&attribute=genreIndex&limit={limit}&offset=0",
            term, limit);
        if (response == null || response.results() == null) return Collections.emptyList();
        return response.results().stream()
            .filter(t -> isValidPreview(t.previewUrl()))
            .map(t -> toTrack(t, genre, null))
            .toList();
    }

    @Override
    public List<Track> fetchByDecade(Decade decade, int limit, int offset) {
        ItunesSearchResponse response = fetchWithRetry(
            "/search?term=music&media=music&entity=song&limit={limit}&offset={offset}",
            limit, offset);
        if (response == null || response.results() == null) return Collections.emptyList();
        return response.results().stream()
            .filter(t -> isValidPreview(t.previewUrl()))
            .filter(t -> isInDecade(t.releaseDate(), decade))
            .map(t -> toTrack(t, null, decade))
            .toList();
    }

    private ItunesSearchResponse fetchWithRetry(String uriTemplate, Object... uriVars) {
        int delay = DELAY_MS;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            sleep(delay);
            try {
                return client.get()
                    .uri(uriTemplate, uriVars)
                    .retrieve()
                    .body(ItunesSearchResponse.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < MAX_RETRIES - 1) {
                    log.warn("iTunes 429 on attempt {}, retrying after {}ms", attempt + 1, delay * 2);
                    delay *= 2;
                } else {
                    throw e;
                }
            }
        }
        return null;
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

    private void sleep(int ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
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
