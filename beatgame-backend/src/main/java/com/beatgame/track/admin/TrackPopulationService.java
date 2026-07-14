package com.beatgame.track.admin;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.TrackImportService;
import com.beatgame.track.provider.DeezerProvider;
import com.beatgame.track.provider.TrackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TrackPopulationService {

    private static final Logger log = LoggerFactory.getLogger(TrackPopulationService.class);
    private static final int PAGE_SIZE = 25;

    private static final List<String> UKRAINIAN_ARTISTS = List.of(
            "MONATIK", "Скрябін", "Антитіла", "Jerry Heil",
            "Бумбокс", "Руслана", "ONUKA", "Kalush Orchestra"
    );

    private final DeezerProvider deezerProvider;
    private final TrackProvider itunesProvider;
    private final TrackImportService trackImportService;

    public TrackPopulationService(DeezerProvider deezerProvider,
                                  @Qualifier("itunesProvider") TrackProvider itunesProvider,
                                  TrackImportService trackImportService) {
        this.deezerProvider = deezerProvider;
        this.itunesProvider = itunesProvider;
        this.trackImportService = trackImportService;
    }

    public PopulationResult populate(String provider, String categoryType, String category, int pages, int offset) {
        TrackProvider trackProvider = "DEEZER".equals(provider) ? deezerProvider : itunesProvider;
        int fetched = 0;
        int saved = 0;

        for (int page = 0; page < pages; page++) {
            int pageOffset = offset + page * PAGE_SIZE;
            List<Track> tracks = fetch(trackProvider, categoryType, category, pageOffset);
            fetched += tracks.size();
            saved += trackImportService.saveNewTracks(tracks);
            log.info("Population {}/{} {}/{}: page {}/{} fetched={} totalSaved={}",
                    provider, categoryType, category, offset, page + 1, pages, tracks.size(), saved);
        }

        return new PopulationResult(provider, categoryType, category, pages, offset, fetched, saved, fetched - saved);
    }

    public PopulateAllResult populateAll(String provider, int pages, int offset) {
        List<PopulationResult> results = new ArrayList<>();

        for (Genre genre : Genre.values()) {
            if (genre == Genre.UKRAINIAN) continue;
            results.add(populate(provider, "GENRE", genre.name(), pages, offset));
        }
        for (Decade decade : Decade.values()) {
            results.add(populate(provider, "DECADE", String.valueOf(decade.from), pages, offset));
        }

        int totalFetched = results.stream().mapToInt(PopulationResult::fetched).sum();
        int totalSaved   = results.stream().mapToInt(PopulationResult::saved).sum();
        int totalSkipped = results.stream().mapToInt(PopulationResult::skipped).sum();
        return new PopulateAllResult(totalFetched, totalSaved, totalSkipped, results);
    }

    public PopulateAllResult populateUkrainian(int tracksPerArtist) {
        List<PopulationResult> results = new ArrayList<>();

        for (String artist : UKRAINIAN_ARTISTS) {
            List<Track> tracks = deezerProvider.fetchByArtist(artist, tracksPerArtist, Genre.UKRAINIAN);
            int saved = trackImportService.saveNewTracks(tracks);
            log.info("Ukrainian population: artist={} fetched={} saved={}", artist, tracks.size(), saved);
            results.add(new PopulationResult("DEEZER", "ARTIST", artist, 1, 0,
                    tracks.size(), saved, tracks.size() - saved));
        }

        int totalFetched = results.stream().mapToInt(PopulationResult::fetched).sum();
        int totalSaved   = results.stream().mapToInt(PopulationResult::saved).sum();
        int totalSkipped = results.stream().mapToInt(PopulationResult::skipped).sum();
        return new PopulateAllResult(totalFetched, totalSaved, totalSkipped, results);
    }

    private List<Track> fetch(TrackProvider provider, String categoryType, String category, int offset) {
        return switch (categoryType) {
            case "GENRE" -> provider.fetchByGenre(Genre.valueOf(category), PAGE_SIZE, offset);
            case "DECADE" -> {
                Decade decade = Decade.fromYear(Integer.parseInt(category));
                if (decade == null) throw new IllegalArgumentException("Unknown decade year: " + category);
                yield provider.fetchByDecade(decade, PAGE_SIZE, offset);
            }
            default -> throw new IllegalArgumentException("Unsupported categoryType: " + categoryType);
        };
    }
}
