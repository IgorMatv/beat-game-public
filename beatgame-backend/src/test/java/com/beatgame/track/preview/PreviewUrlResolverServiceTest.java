package com.beatgame.track.preview;

import com.beatgame.track.Track;
import com.beatgame.track.provider.DeezerProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class PreviewUrlResolverServiceTest {

    @Mock DeezerProvider deezerProvider;
    @Mock RedisTemplate<String, String> redisTemplate;
    @Mock ValueOperations<String, String> valueOps;

    PreviewUrlResolverService resolver;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOps);
        resolver = new PreviewUrlResolverService(deezerProvider, redisTemplate);
    }

    @Test
    void resolve_returnsStoredUrlForItunesTrack() {
        Track track = track("ITUNES", "itunes-100", "https://stored.m4a");

        Map<String, String> result = resolver.resolve(List.of(track));

        assertThat(result).containsEntry("itunes-100", "https://stored.m4a");
        verifyNoInteractions(deezerProvider);
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void resolve_fetchesFreshUrlForDeezerTrack_andCachesIt() {
        Track track = track("DEEZER", "deezer-200", null);
        when(valueOps.get("preview:DEEZER:deezer-200")).thenReturn(null);
        when(deezerProvider.fetchPreviewUrl("deezer-200")).thenReturn("https://fresh.mp3");

        Map<String, String> result = resolver.resolve(List.of(track));

        assertThat(result).containsEntry("deezer-200", "https://fresh.mp3");
        verify(valueOps).get("preview:DEEZER:deezer-200");
        verify(valueOps).set("preview:DEEZER:deezer-200", "https://fresh.mp3", Duration.ofMinutes(55));
    }

    @Test
    void resolve_usesCachedUrl_whenPresentInRedis() {
        Track track = track("DEEZER", "deezer-300", null);
        when(valueOps.get("preview:DEEZER:deezer-300")).thenReturn("https://cached.mp3");

        Map<String, String> result = resolver.resolve(List.of(track));

        assertThat(result).containsEntry("deezer-300", "https://cached.mp3");
        verifyNoInteractions(deezerProvider);
    }

    @Test
    void resolve_excludesTrack_whenDeezerReturnsNull() {
        Track track = track("DEEZER", "deezer-404", null);
        when(valueOps.get("preview:DEEZER:deezer-404")).thenReturn(null);
        when(deezerProvider.fetchPreviewUrl("deezer-404")).thenReturn(null);

        Map<String, String> result = resolver.resolve(List.of(track));

        assertThat(result).doesNotContainKey("deezer-404");
    }

    @Test
    void resolve_handlesMixedProviders() {
        Track itunes = track("ITUNES", "i-1", "https://itunes.m4a");
        Track deezer = track("DEEZER", "d-1", null);
        when(valueOps.get("preview:DEEZER:d-1")).thenReturn("https://deezer.mp3");

        Map<String, String> result = resolver.resolve(List.of(itunes, deezer));

        assertThat(result).containsEntry("i-1", "https://itunes.m4a")
                          .containsEntry("d-1", "https://deezer.mp3");
    }

    @Test
    void resolve_returnsEmptyMap_whenTracksListIsEmpty() {
        Map<String, String> result = resolver.resolve(List.of());
        assertThat(result).isEmpty();
    }

    @Test
    void resolve_skipsNullTrackElements() {
        Track valid = track("ITUNES", "i-1", "https://preview.m4a");
        List<Track> tracks = new java.util.ArrayList<>();
        tracks.add(null);
        tracks.add(valid);

        Map<String, String> result = resolver.resolve(tracks);

        assertThat(result).hasSize(1).containsEntry("i-1", "https://preview.m4a");
    }

    @Test
    void resolve_skipsTrackWithNullProvider() {
        Track noProvider = track(null, "x-1", null);

        Map<String, String> result = resolver.resolve(List.of(noProvider));

        assertThat(result).isEmpty();
    }

    private Track track(String provider, String externalId, String previewUrl) {
        Track t = new Track();
        t.setProvider(provider);
        t.setExternalId(externalId);
        t.setPreviewUrl(previewUrl);
        return t;
    }
}
