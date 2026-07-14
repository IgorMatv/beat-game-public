package com.beatgame.track;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface TrackRepository extends JpaRepository<Track, Long> {

    long countByArchivedFalse();

    long countByGenreAndArchivedFalse(Genre genre);

    long countByDecadeAndArchivedFalse(Short decade);

    boolean existsByExternalIdAndProvider(String externalId, String provider);

    @Query("SELECT t.externalId FROM Track t WHERE t.externalId IN :ids AND t.provider = :provider")
    Set<String> findExistingExternalIds(@Param("ids") List<String> ids, @Param("provider") String provider);

    @Query(value = """
        SELECT * FROM tracks
        WHERE genre = :genre AND archived = false
        ORDER BY play_count ASC, last_used_at ASC NULLS FIRST, RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findWeightedByGenre(@Param("genre") String genre, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM tracks
        WHERE decade = :decade AND archived = false
        ORDER BY play_count ASC, last_used_at ASC NULLS FIRST, RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findWeightedByDecade(@Param("decade") Short decade, @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM tracks
        WHERE archived = false
        ORDER BY play_count ASC, last_used_at ASC NULLS FIRST, RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findWeightedMix(@Param("limit") int limit);

    @Query(value = """
        SELECT * FROM tracks
        WHERE genre = :genre AND archived = false
          AND artist NOT IN (:excludedArtists)
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findDistractorsByGenre(
            @Param("genre") String genre,
            @Param("excludedArtists") List<String> excludedArtists,
            @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM tracks
        WHERE decade = :decade AND archived = false
          AND artist NOT IN (:excludedArtists)
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findDistractorsByDecade(
            @Param("decade") Short decade,
            @Param("excludedArtists") List<String> excludedArtists,
            @Param("limit") int limit);

    @Query(value = """
        SELECT * FROM tracks
        WHERE archived = false
          AND artist NOT IN (:excludedArtists)
        ORDER BY RANDOM()
        LIMIT :limit
        """, nativeQuery = true)
    List<Track> findDistractorsFromAll(
            @Param("excludedArtists") List<String> excludedArtists,
            @Param("limit") int limit);

    @Query("""
        SELECT t FROM Track t
        WHERE t.archived = false
          AND (
            (t.playCount > 50 AND t.lastUsedAt < :thirtyDaysAgo)
            OR (t.playCount < 3 AND t.addedAt < :ninetyDaysAgo)
          )
          AND t.genre = :genre
        """)
    List<Track> findArchiveCandidatesByGenre(
            @Param("genre") Genre genre,
            @Param("thirtyDaysAgo") LocalDateTime thirtyDaysAgo,
            @Param("ninetyDaysAgo") LocalDateTime ninetyDaysAgo);

    @Modifying
    @Query("DELETE FROM Track t WHERE t.archived = true AND t.lastUsedAt < :before")
    int deleteArchivedBefore(@Param("before") LocalDateTime before);
}
