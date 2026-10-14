package com.agentfit.coreapi.domain.project.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.agentfit.coreapi.domain.project.entity.Project;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcProjectRepositoryUuidTest {
    @Test
    void savesAndReadsProjectUsingCanonicalUuidOwnerColumns() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:project-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema-h2.sql'", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        UUID owner = UUID.randomUUID();
        UUID projectId = UUID.randomUUID();
        jdbc.update("INSERT INTO \"USER\" (id, name) VALUES (?, ?)", owner, "owner");
        JdbcProjectRepository repository = new JdbcProjectRepository(jdbc);
        Project project = new Project(projectId.toString(), 0L, owner, "demo", 1L, Instant.now(), Instant.now());

        repository.save(project);

        assertEquals(projectId.toString(), repository.findByIdAndOwnerUuid(project.id(), owner).orElseThrow().id());
        assertEquals(1, repository.findByOwnerUuid(owner).size());
    }
}
