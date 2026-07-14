package com.beatgame.track.seeder;

import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.track.provider.TrackProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackRotationJobTest {

    @Mock TrackRepository trackRepository;
    @Mock TrackProvider deezerProvider;

    @InjectMocks
    TrackRotationJob rotationJob;

    @Test
    void rotate_archivesCandidates_andFetchesReplacements() {
        Track candidate = new Track();
        candidate.setExternalId("old-1");
        candidate.setProvider("DEEZER");
        when(trackRepository.countByGenreAndArchivedFalse(any())).thenReturn(100L);
        when(trackRepository.findArchiveCandidatesByGenre(any(), any(), any()))
            .thenReturn(List.of(candidate));
        Track fresh = new Track();
        fresh.setExternalId("new-1");
        fresh.setProvider("DEEZER");
        when(deezerProvider.fetchByGenre(any(Genre.class), anyInt(), anyInt())).thenReturn(List.of(fresh));
        when(trackRepository.findExistingExternalIds(anyList(), eq("DEEZER"))).thenReturn(Set.of());

        rotationJob.rotate();

        ArgumentCaptor<Track> captor = ArgumentCaptor.forClass(Track.class);
        verify(trackRepository, atLeastOnce()).save(captor.capture());
        boolean archivedCandidateSaved = captor.getAllValues().stream()
            .anyMatch(t -> "old-1".equals(t.getExternalId()) && t.isArchived());
        assertThat(archivedCandidateSaved).isTrue();
        verify(trackRepository, atLeastOnce()).saveAll(List.of(fresh));
    }

    @Test
    void deleteArchived_removesOldArchivedTracks() {
        when(trackRepository.deleteArchivedBefore(any(LocalDateTime.class))).thenReturn(3);

        rotationJob.deleteArchived();

        verify(trackRepository).deleteArchivedBefore(any(LocalDateTime.class));
    }

    @Test
    void rotate_skipsArchiving_whenCategoryBelowMinimum() {
        when(trackRepository.countByGenreAndArchivedFalse(any())).thenReturn(15L);

        rotationJob.rotate();

        verify(trackRepository, never()).findArchiveCandidatesByGenre(any(), any(), any());
    }
}
