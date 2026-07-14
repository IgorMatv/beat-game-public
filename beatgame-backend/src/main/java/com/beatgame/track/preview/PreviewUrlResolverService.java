package com.beatgame.track.preview;

import com.beatgame.track.Track;
import com.beatgame.track.provider.DeezerProvider;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PreviewUrlResolverService {

    private static final Duration CACHE_TTL = Duration.ofMinutes(55);
    private static final String CACHE_KEY_PREFIX = "preview:DEEZER:";

    private final DeezerProvider deezerProvider;
    private final RedisTemplate<String, String> redisTemplate;

    public PreviewUrlResolverService(DeezerProvider deezerProvider, RedisTemplate<String, String> redisTemplate) {
        this.deezerProvider = deezerProvider;
        this.redisTemplate = redisTemplate;
    }

    public Map<String, String> resolve(List<Track> tracks) {
        Map<String, String> result = new HashMap<>();
        for (Track track : tracks) {
            if (track == null) continue;
            String provider = track.getProvider();
            if (provider == null) continue;
            String url = switch (provider) {
                case "ITUNES" -> track.getPreviewUrl();
                case "DEEZER" -> resolveDeezer(track.getExternalId());
                default -> null;
            };
            if (url != null) {
                result.put(track.getExternalId(), url);
            }
        }
        return result;
    }

    private String resolveDeezer(String externalId) {
        if (externalId == null) return null;
        String cacheKey = CACHE_KEY_PREFIX + externalId;
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) return cached;
        String url = deezerProvider.fetchPreviewUrl(externalId);
        if (url != null) {
            redisTemplate.opsForValue().set(cacheKey, url, CACHE_TTL);
        }
        return url;
    }
}
