package com.beatgame.track.seeder;

import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackRepository;
import com.beatgame.track.admin.TrackPopulationService;
import com.beatgame.track.provider.TrackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Component
public class TrackRotationJob {

    private static final Logger log = LoggerFactory.getLogger(TrackRotationJob.class);
    private static final int MIN_ACTIVE_TRACKS = 20;
    private static final double MAX_ARCHIVE_RATIO = 0.2;
    static final int UKRAINIAN_REPLENISH_TRACKS_PER_ARTIST = 10;

    private final TrackRepository trackRepository;
    private final TrackProvider deezerProvider;
    private final TrackPopulationService trackPopulationService;

    public TrackRotationJob(TrackRepository trackRepository,
                            @Qualifier("deezerProvider") TrackProvider deezerProvider,
                            TrackPopulationService trackPopulationService) {
        this.trackRepository = trackRepository;
        this.deezerProvider = deezerProvider;
        this.trackPopulationService = trackPopulationService;
    }

    @Scheduled(cron = "0 0 3 1 * *")
    public void rotate() {
        log.info("Starting monthly track rotation...");
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thirtyDaysAgo = now.minusDays(30);
        LocalDateTime ninetyDaysAgo = now.minusDays(90);

        for (Genre genre : Genre.values()) {
            long activeCount = trackRepository.countByGenreAndArchivedFalse(genre);
            if (activeCount < MIN_ACTIVE_TRACKS) {
                log.info("Skipping rotation for {} — only {} active tracks", genre, activeCount);
                continue;
            }
            List<Track> candidates = trackRepository.findArchiveCandidatesByGenre(genre, thirtyDaysAgo, ninetyDaysAgo);
            int maxToArchive = (int) (activeCount * MAX_ARCHIVE_RATIO);
            List<Track> toArchive = candidates.stream().limit(maxToArchive).toList();

            for (Track t : toArchive) {
                t.setArchived(true);
                t.setLastUsedAt(now);
                trackRepository.save(t);
            }

            if (!toArchive.isEmpty()) {
                if (genre == Genre.UKRAINIAN) {
                    try {
                        trackPopulationService.populateUkrainian(UKRAINIAN_REPLENISH_TRACKS_PER_ARTIST);
                    } catch (Exception e) {
                        log.warn("Failed to replenish Ukrainian tracks", e);
                    }
                } else {
                    try {
                        List<Track> fresh = deezerProvider.fetchByGenre(genre, toArchive.size() * 2, 100);
                        if (!fresh.isEmpty()) {
                            String provider = fresh.get(0).getProvider();
                            List<String> ids = fresh.stream().map(Track::getExternalId).toList();
                            Set<String> existing = trackRepository.findExistingExternalIds(ids, provider);
                            List<Track> toSave = fresh.stream().filter(t -> !existing.contains(t.getExternalId())).toList();
                            if (!toSave.isEmpty()) {
                                trackRepository.saveAll(toSave);
                            }
                        }
                    } catch (Exception e) {
                        log.warn("Failed to fetch replacements for {}", genre, e);
                    }
                }
            }
        }
        log.info("Rotation complete.");
    }

    @Scheduled(cron = "0 0 4 * * *")
    @Transactional
    public void deleteArchived() {
        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        int deleted = trackRepository.deleteArchivedBefore(sevenDaysAgo);
        if (deleted > 0) log.info("Deleted {} archived tracks older than 7 days", deleted);
    }
}
