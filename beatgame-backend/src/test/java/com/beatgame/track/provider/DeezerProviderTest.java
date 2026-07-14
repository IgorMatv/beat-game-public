package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.provider.genre.DeezerGenreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DeezerProviderTest {

    private MockRestServiceServer server;
    private DeezerProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new DeezerProvider(builder.build(), new DeezerGenreMapper());
    }

    @Test
    void fetchByGenre_returnsTracksWithNullPreviewUrl_andFiltersTracksWithoutPreview() {
        server.expect(requestToUriTemplate(
                "http://localhost/chart/{id}/tracks?limit={limit}&index={offset}", "132", 25, 0))
              .andRespond(withSuccess("""
                  {"data":[
                    {"id":1,"title":"Good Song","artist":{"name":"Artist A"},
                     "album":{"cover_medium":"https://cover.jpg","release_date":"2020-01-01"},
                     "preview":"https://preview.mp3"},
                    {"id":2,"title":"No Preview","artist":{"name":"Artist B"},
                     "album":{"cover_medium":"","release_date":"2020-01-01"},
                     "preview":""}
                  ]}
                  """, MediaType.APPLICATION_JSON));

        List<Track> tracks = provider.fetchByGenre(Genre.POP, 25, 0);

        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).getTitle()).isEqualTo("Good Song");
        assertThat(tracks.get(0).getArtist()).isEqualTo("Artist A");
        assertThat(tracks.get(0).getPreviewUrl()).isNull();
        assertThat(tracks.get(0).getProvider()).isEqualTo("DEEZER");
        assertThat(tracks.get(0).getExternalId()).isEqualTo("1");
        assertThat(tracks.get(0).getGenre()).isEqualTo(Genre.POP);
    }

    @Test
    void fetchByDecade_filtersTracksByReleaseDate() {
        server.expect(requestToUriTemplate(
                "http://localhost/search?q=year%3E%3D{from}%20year%3C%3D{to}&limit={limit}&index={offset}",
                1990, 1999, 25, 0))
              .andRespond(withSuccess("""
                  {"data":[
                    {"id":10,"title":"90s Song","artist":{"name":"Artist C"},
                     "album":{"cover_medium":"https://cover.jpg","release_date":"1995-06-15"},
                     "preview":"https://preview.mp3"},
                    {"id":11,"title":"Wrong Decade","artist":{"name":"Artist D"},
                     "album":{"cover_medium":"https://cover.jpg","release_date":"2005-01-01"},
                     "preview":"https://preview2.mp3"}
                  ]}
                  """, MediaType.APPLICATION_JSON));

        List<Track> tracks = provider.fetchByDecade(Decade.D1990, 25, 0);

        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).getTitle()).isEqualTo("90s Song");
        assertThat(tracks.get(0).getDecade()).isEqualTo((short) 1990);
        assertThat(tracks.get(0).getPreviewUrl()).isNull();
    }

    @Test
    void fetchPreviewUrl_returnsFreshPreviewUrl() {
        server.expect(requestToUriTemplate("http://localhost/track/{id}", "42"))
              .andRespond(withSuccess("""
                  {"id":42,"title":"Song","artist":{"name":"Artist"},
                   "album":{"cover_medium":"https://cover.jpg","release_date":"2020-01-01"},
                   "preview":"https://fresh-preview.mp3"}
                  """, MediaType.APPLICATION_JSON));

        String url = provider.fetchPreviewUrl("42");

        assertThat(url).isEqualTo("https://fresh-preview.mp3");
    }

    @Test
    void fetchPreviewUrl_returnsNull_whenTrackHasNoPreview() {
        server.expect(requestToUriTemplate("http://localhost/track/{id}", "99"))
              .andRespond(withSuccess("""
                  {"id":99,"title":"Song","artist":{"name":"Artist"},
                   "album":{"cover_medium":"https://cover.jpg","release_date":"2020-01-01"},
                   "preview":""}
                  """, MediaType.APPLICATION_JSON));

        String url = provider.fetchPreviewUrl("99");

        assertThat(url).isNull();
    }
}
