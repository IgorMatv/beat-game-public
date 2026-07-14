package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;
import com.beatgame.track.provider.genre.ItunesGenreMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ItunesProviderTest {

    private MockRestServiceServer server;
    private ItunesProvider provider;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://localhost");
        server = MockRestServiceServer.bindTo(builder).build();
        provider = new ItunesProvider(builder.build(), new ItunesGenreMapper());
    }

    @Test
    void fetchByGenre_returnsTracksAndFiltersHttpPreviewUrl() {
        server.expect(requestToUriTemplate("http://localhost/search?term={genre}&media=music&entity=song&attribute=genreIndex&limit={limit}&offset={offset}", "Pop", 25, 0))
              .andRespond(withSuccess("""
                  {"resultCount":2,"results":[
                    {"trackId":100,"trackName":"iTunes Song","artistName":"iTunes Artist",
                     "artworkUrl100":"https://artwork.jpg",
                     "previewUrl":"https://preview.m4a",
                     "releaseDate":"2019-05-01T00:00:00Z",
                     "primaryGenreName":"Pop"},
                    {"trackId":101,"trackName":"HTTP Song","artistName":"HTTP Artist",
                     "artworkUrl100":"https://artwork.jpg",
                     "previewUrl":"http://insecure.mp3",
                     "releaseDate":"2019-05-01T00:00:00Z",
                     "primaryGenreName":"Pop"}
                  ]}
                  """, MediaType.APPLICATION_JSON));

        List<Track> tracks = provider.fetchByGenre(Genre.POP, 25, 0);

        assertThat(tracks).hasSize(1);
        assertThat(tracks.get(0).getTitle()).isEqualTo("iTunes Song");
        assertThat(tracks.get(0).getProvider()).isEqualTo("ITUNES");
        assertThat(tracks.get(0).getPreviewUrl()).startsWith("https://");
        server.verify();
    }

    @Test
    void fetchByGenre_latin_usesLatinoTerm() {
        server.expect(requestToUriTemplate(
                "http://localhost/search?term={genre}&media=music&entity=song&attribute=genreIndex&limit={limit}&offset={offset}",
                "Latino", 25, 0))
              .andRespond(withSuccess(
                  "{\"resultCount\":0,\"results\":[]}", MediaType.APPLICATION_JSON));

        List<Track> tracks = provider.fetchByGenre(Genre.LATIN, 25, 0);

        assertThat(tracks).isEmpty();
        server.verify();
    }
}
