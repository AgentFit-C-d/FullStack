package com.agentfit.coreapi.domain.project.repository;

import com.agentfit.coreapi.domain.project.entity.PlanningInput;
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcPlanningInputRepository implements PlanningInputRepository {
    private final JdbcTemplate jdbc;
    JdbcPlanningInputRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public void save(PlanningInput i) {
        jdbc.update("INSERT INTO PLANNING_INPUT(project_id,revision,input_type,file_name,media_type,byte_size,character_count,page_count,raw_content,extracted_text,citations,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
            UUID.fromString(i.projectId()), i.revision(), i.inputType(), i.fileName(), i.mediaType(), i.byteSize(), i.characterCount(), i.pageCount(), i.rawContent(), i.extractedText(), i.citations(), Timestamp.from(i.createdAt()));
    }
    public Optional<PlanningInput> findCurrent(String projectId) {
        return jdbc.query("SELECT project_id,revision,input_type,file_name,media_type,byte_size,character_count,page_count,raw_content,extracted_text,citations,created_at FROM PLANNING_INPUT WHERE project_id=? ORDER BY revision DESC LIMIT 1",
            (rs,n) -> new PlanningInput(rs.getObject(1, UUID.class).toString(),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),(Long)rs.getObject(6),(Integer)rs.getObject(7),(Integer)rs.getObject(8),rs.getBytes(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12).toInstant()), UUID.fromString(projectId)).stream().findFirst();
    }
    public Optional<PlanningInput> findByProjectIdAndRevision(String projectId, long revision) {
        return jdbc.query("SELECT project_id,revision,input_type,file_name,media_type,byte_size,character_count,page_count,raw_content,extracted_text,citations,created_at FROM PLANNING_INPUT WHERE project_id=? AND revision=?",
            (rs,n) -> new PlanningInput(rs.getObject(1, UUID.class).toString(),rs.getLong(2),rs.getString(3),rs.getString(4),rs.getString(5),(Long)rs.getObject(6),(Integer)rs.getObject(7),(Integer)rs.getObject(8),rs.getBytes(9),rs.getString(10),rs.getString(11),rs.getTimestamp(12).toInstant()), UUID.fromString(projectId), revision).stream().findFirst();
    }
    public void scrub(String projectId, long revision) { jdbc.update("UPDATE planning_input SET raw_content=NULL, extracted_text=NULL, citations=NULL WHERE project_id=? AND revision=?", UUID.fromString(projectId), revision); }
    public void deleteByProjectId(String projectId) { jdbc.update("DELETE FROM planning_input WHERE project_id=?", UUID.fromString(projectId)); }
}
