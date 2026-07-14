package com.beatgame.track;

import com.beatgame.track.provider.TrackProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class TrackService {

    private static final Logger log = LoggerFactory.getLogger(TrackService.class);
    private static final int MIN_TRACKS_THRESHOLD = 20;
    private static final int FETCH_PAGE_SIZE = 25;

    private final TrackRepository trackRepository;
    private final TrackProvider deezerProvider;
    private final TrackProvider itunesProvider;
    private final TrackImportService trackImportService;
    private final RedisTemplate<String, String> redisTemplate;

    public TrackService(TrackRepository trackRepository,
                        @Qualifier("deezerProvider") TrackProvider deezerProvider,
                        @Qualifier("itunesProvider") TrackProvider itunesProvider,
                        TrackImportService trackImportService,
                        RedisTemplate<String, String> redisTemplate) {
        this.trackRepository = trackRepository;
        this.deezerProvider = deezerProvider;
        this.itunesProvider = itunesProvider;
        this.trackImportService = trackImportService;
        this.redisTemplate = redisTemplate;
    }

    public List<Track> getTracksForCategory(String category, String categoryType, int count) {
        long activeCount = countActive(category, categoryType);
        if (activeCount < MIN_TRACKS_THRESHOLD) {
            String cacheKey = "ondemand:" + category + ":" + categoryType;
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                fetchAndSave(category, categoryType);
                redisTemplate.opsForValue().set(cacheKey, "fetched", Duration.ofHours(1));
            }
        }
        List<Track> tracks = findWeighted(category, categoryType, count);
        markUsed(tracks);
        return tracks;
    }

    private long countActive(String category, String categoryType) {
        return switch (categoryType) {
            case "GENRE"  -> trackRepository.countByGenreAndArchivedFalse(Genre.valueOf(category));
            case "DECADE" -> trackRepository.countByDecadeAndArchivedFalse(Short.parseShort(category));
            case "MIX"    -> trackRepository.countByArchivedFalse();
            default       -> throw new IllegalArgumentException("Unknown categoryType: " + categoryType);
        };
    }

    private List<Track> findWeighted(String category, String categoryType, int count) {
        return switch (categoryType) {
            case "GENRE"  -> trackRepository.findWeightedByGenre(category, count);
            case "DECADE" -> trackRepository.findWeightedByDecade(Short.parseShort(category), count);
            case "MIX"    -> trackRepository.findWeightedMix(count);
            default       -> throw new IllegalArgumentException("Unknown categoryType: " + categoryType);
        };
    }

    private void fetchAndSave(String category, String categoryType) {
        if ("GENRE".equals(categoryType) && "UKRAINIAN".equals(category)) {
            log.info("On-demand fetch skipped for UKRAINIAN — populate via /api/admin/tracks/populate/ukrainian");
            return;
        }
        log.info("On-demand fetch triggered for {} {}", categoryType, category);
        try {
            List<Track> tracks = switch (categoryType) {
                case "GENRE"  -> deezerProvider.fetchByGenre(Genre.valueOf(category), FETCH_PAGE_SIZE, 0);
                case "DECADE" -> {
                    Decade d = Decade.fromYear(Integer.parseInt(category));
                    if (d == null) {
                        log.warn("Cannot map decade year {} to known Decade, skipping fetch", category);
                        yield List.of();
                    }
                    yield deezerProvider.fetchByDecade(d, FETCH_PAGE_SIZE, 0);
                }
                default -> List.of();
            };
            trackImportService.saveNewTracks(tracks);
        } catch (Exception e) {
            log.warn("On-demand Deezer fetch failed for {} {}, trying iTunes", categoryType, category, e);
            try {
                List<Track> tracks = switch (categoryType) {
                    case "GENRE"  -> itunesProvider.fetchByGenre(Genre.valueOf(category), FETCH_PAGE_SIZE, 0);
                    case "DECADE" -> {
                        Decade d = Decade.fromYear(Integer.parseInt(category));
                        if (d == null) {
                            log.warn("Cannot map decade year {} to known Decade, skipping iTunes fetch", category);
                            yield List.of();
                        }
                        yield itunesProvider.fetchByDecade(d, FETCH_PAGE_SIZE, 0);
                    }
                    default -> List.of();
                };
                trackImportService.saveNewTracks(tracks);
            } catch (Exception ex) {
                log.error("iTunes fallback also failed for {} {}", categoryType, category, ex);
            }
        }
    }

    private void markUsed(List<Track> tracks) {
        if (tracks.isEmpty()) return;
        LocalDateTime now = LocalDateTime.now();
        for (Track t : tracks) {
            t.setPlayCount(t.getPlayCount() + 1);
            t.setLastUsedAt(now);
        }
        trackRepository.saveAll(tracks);
    }
}
