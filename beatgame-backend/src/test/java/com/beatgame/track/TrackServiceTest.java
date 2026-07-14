package com.beatgame.track;

import com.beatgame.track.provider.TrackProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackServiceTest {

    @Mock TrackRepository trackRepository;
    @Mock TrackProvider deezerProvider;
    @Mock TrackProvider itunesProvider;
    @Mock TrackImportService trackImportService;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    TrackService trackService;

    @BeforeEach
    void setUp() {
        trackService = new TrackService(trackRepository, deezerProvider, itunesProvider, trackImportService, redisTemplate);
    }

    @Test
    void getTracksForCategory_returnsWeightedTracksFromDb_whenSufficientCount() {
        when(trackRepository.countByGenreAndArchivedFalse(Genre.POP)).thenReturn(50L);
        Track t1 = track("Song A");
        Track t2 = track("Song B");
        when(trackRepository.findWeightedByGenre("POP", 5)).thenReturn(List.of(t1, t2));

        List<Track> result = trackService.getTracksForCategory("POP", "GENRE", 5);

        assertThat(result).containsExactly(t1, t2);
        verify(trackRepository).saveAll(anyList());
        assertThat(t1.getPlayCount()).isEqualTo(1);
        assertThat(t1.getLastUsedAt()).isNotNull();
    }

    @Test
    void getTracksForCategory_fetchesFromProvider_whenCountBelowThreshold() {
        when(trackRepository.countByGenreAndArchivedFalse(Genre.POP)).thenReturn(5L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("ondemand:POP:GENRE")).thenReturn(null);
        Track freshTrack = track("Fresh Song");
        when(deezerProvider.fetchByGenre(Genre.POP, 25, 0)).thenReturn(List.of(freshTrack));
        when(trackRepository.findWeightedByGenre("POP", 5)).thenReturn(List.of(freshTrack));

        List<Track> result = trackService.getTracksForCategory("POP", "GENRE", 5);

        verify(deezerProvider).fetchByGenre(Genre.POP, 25, 0);
        verify(trackImportService).saveNewTracks(List.of(freshTrack));
        verify(valueOps).set(eq("ondemand:POP:GENRE"), eq("fetched"), any());
        assertThat(result).contains(freshTrack);
    }

    @Test
    void getTracksForCategory_skipsFetch_whenRedisCachePresent() {
        when(trackRepository.countByGenreAndArchivedFalse(Genre.POP)).thenReturn(5L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("ondemand:POP:GENRE")).thenReturn("fetched");
        when(trackRepository.findWeightedByGenre("POP", 5)).thenReturn(List.of());

        trackService.getTracksForCategory("POP", "GENRE", 5);

        verify(deezerProvider, never()).fetchByGenre(any(), anyInt(), anyInt());
    }

    @Test
    void getTracksForCategory_fallsBackToItunes_whenDeezerThrows() {
        when(trackRepository.countByGenreAndArchivedFalse(Genre.POP)).thenReturn(5L);
        when(redisTemplate.opsForValue()).thenReturn(valueOps);
        when(valueOps.get("ondemand:POP:GENRE")).thenReturn(null);
        when(deezerProvider.fetchByGenre(Genre.POP, 25, 0)).thenThrow(new RuntimeException("Deezer down"));
        Track itunesTrack = track("iTunes Song");
        when(itunesProvider.fetchByGenre(Genre.POP, 25, 0)).thenReturn(List.of(itunesTrack));
        when(trackRepository.findWeightedByGenre("POP", 5)).thenReturn(List.of(itunesTrack));

        List<Track> result = trackService.getTracksForCategory("POP", "GENRE", 5);

        verify(itunesProvider).fetchByGenre(Genre.POP, 25, 0);
        verify(trackImportService).saveNewTracks(List.of(itunesTrack));
        assertThat(result).contains(itunesTrack);
    }

    private Track track(String title) {
        Track t = new Track();
        t.setTitle(title);
        t.setArtist("Artist");
        t.setProvider("DEEZER");
        t.setExternalId("ext-" + title.hashCode());
        t.setGenre(Genre.POP);
        return t;
    }
}
