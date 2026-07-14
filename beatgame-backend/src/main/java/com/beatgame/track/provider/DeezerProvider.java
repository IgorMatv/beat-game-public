package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.provider.genre.DeezerGenreMapper;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;

@Component("deezerProvider")
public class DeezerProvider implements TrackProvider {

    private static final Logger log = LoggerFactory.getLogger(DeezerProvider.class);
    private static final int DELAY_MS = 200;
    private static final int MAX_RETRIES = 3;

    private final RestClient client;
    private final DeezerGenreMapper genreMapper;

    public DeezerProvider(@Qualifier("deezerClient") RestClient client, DeezerGenreMapper genreMapper) {
        this.client = client;
        this.genreMapper = genreMapper;
    }

    public List<Track> fetchByArtist(String artistName, int limit, Genre genre) {
        DeezerSearchResponse response = fetchWithRetry(
            "/search?q=artist:%22{artist}%22&limit={limit}", artistName, limit);
        if (response == null || response.data() == null) return Collections.emptyList();
        return response.data().stream()
            .filter(t -> t.preview() != null && !t.preview().isBlank())
            .map(t -> toTrack(t, genre, null))
            .toList();
    }

    @Override
    public List<Track> fetchByGenre(Genre genre, int limit, int offset) {
        if (genre == Genre.UKRAINIAN) return Collections.emptyList();
        String genreId = genreMapper.map(genre);
        DeezerSearchResponse response = fetchWithRetry(
            "/chart/{id}/tracks?limit={limit}&index={offset}", genreId, limit, offset);
        if (response == null || response.data() == null) return Collections.emptyList();
        return response.data().stream()
            .filter(t -> t.preview() != null && !t.preview().isBlank())
            .map(t -> toTrack(t, genre, null))
            .toList();
    }

    @Override
    public List<Track> fetchByDecade(Decade decade, int limit, int offset) {
        DeezerSearchResponse response = fetchWithRetry(
            "/search?q=year%3E%3D{from}%20year%3C%3D{to}&limit={limit}&index={offset}",
            decade.from, decade.to, limit, offset);
        if (response == null || response.data() == null) return Collections.emptyList();
        return response.data().stream()
            .filter(t -> t.preview() != null && !t.preview().isBlank())
            .filter(t -> isInDecade(t, decade))
            .map(t -> toTrack(t, null, decade))
            .toList();
    }

    public String fetchPreviewUrl(String externalId) {
        int delay = DELAY_MS;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                DeezerTrackDto track = client.get()
                    .uri("/track/{id}", externalId)
                    .retrieve()
                    .body(DeezerTrackDto.class);
                if (track == null || track.preview() == null || track.preview().isBlank()) return null;
                return track.preview();
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < MAX_RETRIES - 1) {
                    sleep(delay);
                    delay *= 2;
                } else {
                    log.warn("Failed to fetch preview URL for Deezer track {}: {}", externalId, e.getStatusCode());
                    return null;
                }
            }
        }
        return null;
    }

    private DeezerSearchResponse fetchWithRetry(String uriTemplate, Object... uriVars) {
        int delay = DELAY_MS;
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            sleep(delay);
            try {
                return client.get()
                    .uri(uriTemplate, uriVars)
                    .retrieve()
                    .body(DeezerSearchResponse.class);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS && attempt < MAX_RETRIES - 1) {
                    delay *= 2;
                } else {
                    throw e;
                }
            }
        }
        return null;
    }

    private boolean isInDecade(DeezerTrackDto t, Decade decade) {
        if (t.album() == null || t.album().releaseDate() == null) return false;
        try {
            int year = Integer.parseInt(t.album().releaseDate().substring(0, 4));
            return year >= decade.from && year <= decade.to;
        } catch (Exception e) {
            return false;
        }
    }

    private Track toTrack(DeezerTrackDto dto, Genre genre, Decade decade) {
        Track track = new Track();
        track.setTitle(dto.title());
        track.setArtist(dto.artist() != null ? dto.artist().name() : "Unknown");
        track.setGenre(genre);
        track.setCoverUrl(dto.album() != null ? dto.album().coverMedium() : null);
        track.setProvider("DEEZER");
        track.setExternalId(String.valueOf(dto.id()));
        if (decade != null) {
            track.setDecade((short) decade.from);
        } else if (dto.album() != null && dto.album().releaseDate() != null) {
            try {
                int year = Integer.parseInt(dto.album().releaseDate().substring(0, 4));
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
    private record DeezerSearchResponse(List<DeezerTrackDto> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeezerTrackDto(long id, String title, DeezerArtistDto artist, DeezerAlbumDto album, String preview) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeezerArtistDto(String name) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record DeezerAlbumDto(
        @JsonProperty("cover_medium") String coverMedium,
        @JsonProperty("release_date") String releaseDate) {}
}
