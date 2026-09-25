package com.pulseops.ai;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface AiAnalysisRepository extends JpaRepository<AiAnalysis, Long> {

    Optional<AiAnalysis> findByIncidentId(Long incidentId);

    /** Recovers analyses left PENDING by a crash or restart, so the UI offers "Retry" instead of spinning forever. */
    @Modifying
    @Query("""
            update AiAnalysis a set a.status = com.pulseops.ai.AnalysisStatus.FAILED,
                   a.error = 'Interrupted (server restarted or timed out). Retry to run it again.', a.updatedAt = :now
            where a.status = com.pulseops.ai.AnalysisStatus.PENDING and a.updatedAt < :cutoff
            """)
    int failStuckAnalyses(@Param("cutoff") Instant cutoff, @Param("now") Instant now);

    @Modifying
    @Query("""
            update AiAnalysis a set a.postmortemStatus = com.pulseops.ai.PostmortemStatus.FAILED, a.updatedAt = :now
            where a.postmortemStatus = com.pulseops.ai.PostmortemStatus.PENDING and a.updatedAt < :cutoff
            """)
    int failStuckPostmortems(@Param("cutoff") Instant cutoff, @Param("now") Instant now);
}
