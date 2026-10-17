package com.agentfit.coreapi.domain.auth.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class JdbcUserAccountRepositoryUuidTest {

    @Test
    void createsAndFindsGithubAccountUsingUuidPrimaryKey() {
        DataSource dataSource = new DriverManagerDataSource(
            "jdbc:h2:mem:user-account-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("CREATE TABLE \"USER\" (id UUID PRIMARY KEY, name VARCHAR(255) NOT NULL, "
            + "github_user_id BIGINT UNIQUE, github_login VARCHAR(255) UNIQUE)");
        JdbcUserAccountRepository repository = new JdbcUserAccountRepository(jdbc);

        var created = repository.createFromGithub(129066593L, "ayanemoona", "ayanemoona");

        assertNotNull(created.uuid());
        assertEquals(created, repository.findByGithubUserId(129066593L).orElseThrow());
        assertEquals(created, repository.findByUuid(created.uuid()).orElseThrow());
    }
}
