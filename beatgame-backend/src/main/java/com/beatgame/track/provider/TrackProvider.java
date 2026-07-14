package com.beatgame.track.provider;

import com.beatgame.track.Decade;
import com.beatgame.track.Genre;
import com.beatgame.track.Track;

import java.util.List;

public interface TrackProvider {
    /** Fetch up to {@code limit} tracks for genre, starting at {@code offset}. Filters out tracks without a preview. Preview URLs may be null and must be resolved before serving to clients. */
    List<Track> fetchByGenre(Genre genre, int limit, int offset);

    /** Fetch up to {@code limit} tracks for decade, starting at {@code offset}. Filters by release_date in Java. */
    List<Track> fetchByDecade(Decade decade, int limit, int offset);
}
