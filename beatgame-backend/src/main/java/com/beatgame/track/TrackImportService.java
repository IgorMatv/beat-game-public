package com.beatgame.track;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class TrackImportService {

    private final TrackRepository trackRepository;

    public TrackImportService(TrackRepository trackRepository) {
        this.trackRepository = trackRepository;
    }

    public long count() {
        return trackRepository.count();
    }

    public int saveNewTracks(List<Track> tracks) {
        if (tracks.isEmpty()) return 0;
        String provider = tracks.get(0).getProvider();
        List<String> externalIds = tracks.stream().map(Track::getExternalId).toList();
        Set<String> existing = trackRepository.findExistingExternalIds(externalIds, provider);
        List<Track> toSave = tracks.stream().filter(t -> !existing.contains(t.getExternalId())).toList();
        if (!toSave.isEmpty()) {
            trackRepository.saveAll(toSave);
        }
        return toSave.size();
    }
}
