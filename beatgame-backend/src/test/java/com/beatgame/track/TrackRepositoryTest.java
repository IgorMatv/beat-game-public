package com.beatgame.track;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class TrackRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    TrackRepository trackRepository;

    @BeforeEach
    void clearTable() {
        trackRepository.deleteAll();
    }

    private Track activeTrack(Genre genre, short decade, String externalId) {
        Track t = new Track();
        t.setTitle("Active Track");
        t.setArtist("Artist Active");
        t.setGenre(genre);
        t.setDecade(decade);
        t.setProvider("DEEZER");
        t.setExternalId(externalId);
        t.setArchived(false);
        return t;
    }

    private Track archivedTrack(Genre genre, short decade, String externalId) {
        Track t = new Track();
        t.setTitle("Archived Track");
        t.setArtist("Artist Archived");
        t.setGenre(genre);
        t.setDecade(decade);
        t.setProvider("DEEZER");
        t.setExternalId(externalId);
        t.setArchived(true);
        return t;
    }

    @Test
    void findWeightedByGenre_excludesArchivedTracks() {
        trackRepository.save(activeTrack(Genre.POP, (short) 2000, "active-genre-1"));
        trackRepository.save(archivedTrack(Genre.POP, (short) 2000, "archived-genre-1"));

        List<Track> result = trackRepository.findWeightedByGenre("POP", 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isArchived()).isFalse();
    }

    @Test
    void findWeightedByDecade_excludesArchivedTracks() {
        trackRepository.save(activeTrack(Genre.ROCK, (short) 1990, "active-decade-1"));
        trackRepository.save(archivedTrack(Genre.ROCK, (short) 1990, "archived-decade-1"));

        List<Track> result = trackRepository.findWeightedByDecade((short) 1990, 10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isArchived()).isFalse();
    }

    @Test
    void findWeightedMix_excludesArchivedTracks() {
        trackRepository.save(activeTrack(Genre.JAZZ, (short) 2010, "active-mix-1"));
        trackRepository.save(archivedTrack(Genre.JAZZ, (short) 2010, "archived-mix-1"));

        List<Track> result = trackRepository.findWeightedMix(10);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).isArchived()).isFalse();
    }
}
