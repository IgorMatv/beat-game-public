package com.beatgame.track.provider.genre;

import com.beatgame.track.Genre;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class GenreMapperTest {

    @Test
    void deezerMapper_mapsAllGenresToNumericIds() {
        DeezerGenreMapper mapper = new DeezerGenreMapper();
        assertThat(mapper.map(Genre.POP)).isEqualTo("132");
        assertThat(mapper.map(Genre.ROCK)).isEqualTo("152");
        assertThat(mapper.map(Genre.HIP_HOP)).isEqualTo("116");
        assertThat(mapper.map(Genre.ELECTRONIC)).isEqualTo("106");
        assertThat(mapper.map(Genre.RNB)).isEqualTo("165");
        assertThat(mapper.map(Genre.JAZZ)).isEqualTo("129");
        assertThat(mapper.map(Genre.CLASSICAL)).isEqualTo("98");
        assertThat(mapper.map(Genre.METAL)).isEqualTo("155");
        assertThat(mapper.map(Genre.COUNTRY)).isEqualTo("84");
        assertThat(mapper.map(Genre.LATIN)).isEqualTo("197");
    }

    @Test
    void itunesMapper_mapsAllGenresToNames() {
        ItunesGenreMapper mapper = new ItunesGenreMapper();
        assertThat(mapper.map(Genre.POP)).isEqualTo("Pop");
        assertThat(mapper.map(Genre.ROCK)).isEqualTo("Rock");
        assertThat(mapper.map(Genre.HIP_HOP)).isEqualTo("Hip-Hop/Rap");
        assertThat(mapper.map(Genre.ELECTRONIC)).isEqualTo("Electronic");
        assertThat(mapper.map(Genre.RNB)).isEqualTo("R&B/Soul");
        assertThat(mapper.map(Genre.JAZZ)).isEqualTo("Jazz");
        assertThat(mapper.map(Genre.CLASSICAL)).isEqualTo("Classical");
        assertThat(mapper.map(Genre.METAL)).isEqualTo("Metal");
        assertThat(mapper.map(Genre.COUNTRY)).isEqualTo("Country");
        assertThat(mapper.map(Genre.LATIN)).isEqualTo("Latino");
    }
}
