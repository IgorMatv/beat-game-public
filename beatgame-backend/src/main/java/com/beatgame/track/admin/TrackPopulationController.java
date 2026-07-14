package com.beatgame.track.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/tracks")
public class TrackPopulationController {

    private final TrackPopulationService populationService;
    private final String adminToken;

    public TrackPopulationController(TrackPopulationService populationService,
                                     @Value("${admin.token:}") String adminToken) {
        this.populationService = populationService;
        this.adminToken = adminToken;
    }

    @PostMapping("/populate/ukrainian")
    public PopulateAllResult populateUkrainian(
            @RequestHeader("X-Admin-Token") String token,
            @RequestParam(defaultValue = "50") int tracksPerArtist) {

        checkToken(token);
        if (tracksPerArtist < 1 || tracksPerArtist > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "tracksPerArtist must be 1–100");
        }
        return populationService.populateUkrainian(tracksPerArtist);
    }

    @PostMapping("/populate/all")
    public PopulateAllResult populateAll(
            @RequestHeader("X-Admin-Token") String token,
            @RequestParam String provider,
            @RequestParam(defaultValue = "4") int pages,
            @RequestParam(defaultValue = "0") int offset) {

        checkToken(token);
        validateCommon(provider, pages, offset);
        return populationService.populateAll(provider, pages, offset);
    }

    @PostMapping("/populate")
    public PopulationResult populate(
            @RequestHeader("X-Admin-Token") String token,
            @RequestParam String provider,
            @RequestParam String categoryType,
            @RequestParam String category,
            @RequestParam(defaultValue = "4") int pages,
            @RequestParam(defaultValue = "0") int offset) {

        checkToken(token);
        validateCommon(provider, pages, offset);
        if (!Set.of("GENRE", "DECADE").contains(categoryType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "categoryType must be GENRE or DECADE");
        }
        try {
            return populationService.populate(provider, categoryType, category, pages, offset);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private void checkToken(String token) {
        if (adminToken.isBlank() || !adminToken.equals(token)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private void validateCommon(String provider, int pages, int offset) {
        if (!Set.of("DEEZER", "ITUNES").contains(provider)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "provider must be DEEZER or ITUNES");
        }
        if (pages < 1 || pages > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "pages must be 1–20");
        }
        if (offset < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "offset must be >= 0");
        }
    }
}
