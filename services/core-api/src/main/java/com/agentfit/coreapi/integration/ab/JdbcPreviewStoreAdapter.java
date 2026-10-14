package com.agentfit.coreapi.integration.ab;

import com.agentfit.coreapi.catalog.PreviewCreationService;
import com.agentfit.coreapi.configuration.export.StoredPreviewState;
import com.agentfit.coreapi.configuration.persistence.JdbcPreviewMetadataWriter;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/** Persists only B5-B8 Preview metadata in an A-owned transaction. */
@Component
public final class JdbcPreviewStoreAdapter implements PreviewCreationService.PreviewStore {
    private final DataSource dataSource;
    private final TransactionTemplate transactions;
    private final JdbcPreviewMetadataWriter writer = new JdbcPreviewMetadataWriter();

    public JdbcPreviewStoreAdapter(DataSource dataSource) {
        this.dataSource = dataSource;
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
    }

    @Override
    public StoredPreviewState saveIfUnchanged(PreviewCreationService.TrustedContext context,
            PreviewCreationService.Draft draft) {
        return transactions.execute(status -> {
            var connection = DataSourceUtils.getConnection(dataSource);
            try {
                var now = Instant.now();
                return writer.write(connection, UUID.randomUUID().toString(), draft, now,
                    now.plus(Duration.ofMinutes(30)));
            } catch (SQLException exception) {
                throw new IllegalStateException("Preview transaction failed", exception);
            }
        });
    }
}
