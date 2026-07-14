// beatgame-backend/src/main/java/com/beatgame/track/seeder/InitialDataSeeder.java
package com.beatgame.track.seeder;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.TrackImportService;
import com.beatgame.track.provider.TrackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.initial-data-seeder.enabled", havingValue = "true", matchIfMissing = true)
public class InitialDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(InitialDataSeeder.class);
    private static final int PAGE_SIZE = 25;
    static final int PAGES_PER_CATEGORY = 4;

    private final TrackImportService trackImportService;
    private final TrackProvider deezerProvider;
    private final TrackProvider itunesProvider;

    public InitialDataSeeder(TrackImportService trackImportService,
                             @Qualifier("deezerProvider") TrackProvider deezerProvider,
                             @Qualifier("itunesProvider") TrackProvider itunesProvider) {
        this.trackImportService = trackImportService;
        this.deezerProvider = deezerProvider;
        this.itunesProvider = itunesProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seed() {
        if (trackImportService.count() > 0) {
            log.info("DB already has tracks, skipping initial seed");
            return;
        }
        log.info("Starting initial track seeding...");
        for (Genre genre : Genre.values()) {
            tryFetch(() -> {
                for (int page = 0; page < PAGES_PER_CATEGORY; page++) {
                    trackImportService.saveNewTracks(deezerProvider.fetchByGenre(genre, PAGE_SIZE, page * PAGE_SIZE));
                }
            }, "Deezer genre " + genre);
            tryFetch(() -> {
                for (int page = 0; page < PAGES_PER_CATEGORY; page++) {
                    trackImportService.saveNewTracks(itunesProvider.fetchByGenre(genre, PAGE_SIZE, page * PAGE_SIZE));
                }
            }, "iTunes genre " + genre);
        }
        for (Decade decade : Decade.values()) {
            tryFetch(() -> {
                for (int page = 0; page < PAGES_PER_CATEGORY; page++) {
                    trackImportService.saveNewTracks(deezerProvider.fetchByDecade(decade, PAGE_SIZE, page * PAGE_SIZE));
                }
            }, "decade " + decade);
        }
        log.info("Initial seeding complete. Total tracks: {}", trackImportService.count());
    }

    private void tryFetch(Runnable action, String label) {
        try {
            action.run();
        } catch (Exception e) {
            log.warn("Failed to seed {}", label, e);
        }
    }
}
