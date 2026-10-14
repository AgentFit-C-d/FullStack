package com.agentfit.coreapi.domain.auth.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
class JdbcUserAccountRepository implements UserAccountRepository {
    private final JdbcTemplate jdbcTemplate;

    JdbcUserAccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public Optional<UserAccount> findByGithubUserId(long githubUserId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, name, github_user_id, github_login FROM \"USER\" WHERE github_user_id = ?",
                (rs, rowNum) -> new UserAccount(rs.getLong("id"), rs.getString("name"),
                    rs.getLong("github_user_id"), rs.getString("github_login")),
                githubUserId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findById(long id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, name, github_user_id, github_login FROM \"USER\" WHERE id = ?",
                (rs, rowNum) -> new UserAccount(rs.getLong("id"), rs.getString("name"),
                    rs.getLong("github_user_id"), rs.getString("github_login")),
                id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserAccount> findByUuid(UUID id) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                "SELECT id, name, github_user_id, github_login FROM \"USER\" WHERE id = ?",
                (rs, rowNum) -> new UserAccount(rs.getObject("id", UUID.class), rs.getString("name"),
                    rs.getLong("github_user_id"), rs.getString("github_login")), id));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName) {
        Long id = jdbcTemplate.queryForObject(
            "INSERT INTO \"USER\" (name, github_user_id, github_login) VALUES (?, ?, ?) RETURNING id",
            Long.class, displayName, githubUserId, githubLogin);
        if (id == null) {
            throw new IllegalStateException("USER insert did not return an id");
        }
        return new UserAccount(id, displayName, githubUserId, githubLogin);
    }
}



