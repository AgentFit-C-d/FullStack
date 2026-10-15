package com.agentfit.coreapi.domain.analysis.repository;

import com.agentfit.coreapi.domain.analysis.entity.AnalysisAdmission;
import java.sql.Timestamp;
import java.time.Instant;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcAnalysisAdmissionRepository implements AnalysisAdmissionRepository {
    private final JdbcTemplate jdbc;
    JdbcAnalysisAdmissionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public AnalysisAdmission lock() { return jdbc.queryForObject("SELECT id,active_count,updated_at FROM ANALYSIS_ADMISSION WHERE id=1 FOR UPDATE", (rs,n)->new AnalysisAdmission(rs.getShort(1),rs.getShort(2),rs.getTimestamp(3).toInstant())); }
    public void increment() { jdbc.update("UPDATE ANALYSIS_ADMISSION SET active_count=active_count+1,updated_at=? WHERE id=1", Timestamp.from(Instant.now())); }
    public void decrement() { jdbc.update("UPDATE ANALYSIS_ADMISSION SET active_count=GREATEST(active_count-1,0),updated_at=? WHERE id=1", Timestamp.from(Instant.now())); }
}
