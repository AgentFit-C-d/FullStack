package com.agentfit.coreapi.configuration.persistence;

import com.agentfit.coreapi.domain.project.service.ProjectBDeletionPort;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;

/** Bridges A's outer transaction to B's caller-owned JDBC deletion contract. */
@Component
public final class JdbcProjectBDeletionPort implements ProjectBDeletionPort {
    private final DataSource dataSource;
    private final JdbcProjectBDataDeleter deleter;

    public JdbcProjectBDeletionPort(DataSource dataSource) {
        this.dataSource = dataSource;
        this.deleter = new JdbcProjectBDataDeleter();
    }

    @Override
    public void delete(String projectId) {
        try {
            deleter.delete(DataSourceUtils.getConnection(dataSource), projectId);
        } catch (SQLException exception) {
            throw new IllegalStateException("B project deletion failed", exception);
        }
    }
}
