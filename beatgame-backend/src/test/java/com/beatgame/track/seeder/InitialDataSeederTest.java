// beatgame-backend/src/test/java/com/beatgame/track/seeder/InitialDataSeederTest.java
package com.beatgame.track.seeder;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackImportService;
import com.beatgame.track.provider.TrackProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InitialDataSeederTest {

    @Mock TrackImportService trackImportService;
    @Mock TrackProvider deezerProvider;
    @Mock TrackProvider itunesProvider;

    InitialDataSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new InitialDataSeeder(trackImportService, deezerProvider, itunesProvider);
    }

    @Test
    void seed_doesNothing_whenDbNotEmpty() {
        when(trackImportService.count()).thenReturn(100L);

        seeder.seed();

        verify(deezerProvider, never()).fetchByGenre(any(), anyInt(), anyInt());
        verify(itunesProvider, never()).fetchByGenre(any(), anyInt(), anyInt());
        verify(deezerProvider, never()).fetchByDecade(any(), anyInt(), anyInt());
    }

    @Test
    void seed_fetchesFromBothProvidersForAllGenres_whenDbEmpty() {
        when(trackImportService.count()).thenReturn(0L);
        when(deezerProvider.fetchByGenre(any(Genre.class), anyInt(), anyInt()))
            .thenReturn(List.of(mockTrack("d-1", "DEEZER")));
        when(itunesProvider.fetchByGenre(any(Genre.class), anyInt(), anyInt()))
            .thenReturn(List.of(mockTrack("i-1", "ITUNES")));
        when(deezerProvider.fetchByDecade(any(Decade.class), anyInt(), anyInt()))
            .thenReturn(List.of());

        seeder.seed();

        int genrePages = Genre.values().length * InitialDataSeeder.PAGES_PER_CATEGORY;
        int decadePages = Decade.values().length * InitialDataSeeder.PAGES_PER_CATEGORY;
        verify(deezerProvider, times(genrePages)).fetchByGenre(any(Genre.class), eq(25), anyInt());
        verify(itunesProvider, times(genrePages)).fetchByGenre(any(Genre.class), eq(25), anyInt());
        verify(deezerProvider, times(decadePages)).fetchByDecade(any(Decade.class), eq(25), anyInt());
        verify(itunesProvider, never()).fetchByDecade(any(), anyInt(), anyInt());
    }

    @Test
    void seed_delegatesSaveToTrackImportService() {
        when(trackImportService.count()).thenReturn(0L);
        Track track = mockTrack("d-1", "DEEZER");
        when(deezerProvider.fetchByGenre(any(), anyInt(), anyInt())).thenReturn(List.of(track));
        when(deezerProvider.fetchByDecade(any(), anyInt(), anyInt())).thenReturn(List.of());
        when(itunesProvider.fetchByGenre(any(), anyInt(), anyInt())).thenReturn(List.of());

        seeder.seed();

        verify(trackImportService, atLeastOnce()).saveNewTracks(anyList());
    }

    private Track mockTrack(String externalId, String provider) {
        Track t = new Track();
        t.setTitle("Test");
        t.setArtist("Artist");
        t.setProvider(provider);
        t.setExternalId(externalId);
        if ("ITUNES".equals(provider)) {
            t.setPreviewUrl("https://preview.mp3");
        }
        return t;
    }
}
