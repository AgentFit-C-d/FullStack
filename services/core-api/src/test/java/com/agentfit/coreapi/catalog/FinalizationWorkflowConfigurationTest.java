package com.agentfit.coreapi.catalog;

import com.agentfit.autoconfigure.CatalogSourceConfiguration;
import com.agentfit.autoconfigure.FinalizationWorkflowConfiguration;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.autoconfigure.AutoConfigurations;

import static org.junit.jupiter.api.Assertions.*;

class FinalizationWorkflowConfigurationTest {
    @TempDir Path directory;

    @Test
    void noApprovalOrDownloadActivatesWithoutAllTrustedPorts() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = configured(hash)
            .withBean(FinalizationWorkflowConfiguration.ServerIssuedIds.class,
                () -> new FinalizationWorkflowConfiguration.ServerIssuedIds() {
                    public String approvalId() { return "approval-1"; }
                    public String generationId() { return "generation-1"; }
                })
            .withBean(Clock.class,
                () -> Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC));

        runner.run(context -> {
            assertFalse(context.containsBean("approvalCreationService"));
            assertFalse(context.containsBean("configurationDownloadService"));
        });
        runner.withBean(ApprovalCreationService.TrustedPreviewReader.class,
                () -> (projectId, previewId) -> null)
            .run(context -> {
                assertFalse(context.containsBean("approvalCreationService"));
                assertFalse(context.containsBean("configurationDownloadService"));
            });
    }

    @Test
    void approvalAndDownloadActivateIndependentlyWithTheirOwnPersistencePorts() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = configured(hash)
            .withBean(FinalizationWorkflowConfiguration.ServerIssuedIds.class,
                () -> new FinalizationWorkflowConfiguration.ServerIssuedIds() {
                    public String approvalId() { return "approval-1"; }
                    public String generationId() { return "generation-1"; }
                })
            .withBean(Clock.class,
                () -> Clock.fixed(Instant.parse("2026-10-10T12:00:00Z"), ZoneOffset.UTC))
            .withBean(ApprovalCreationService.TrustedPreviewReader.class,
                () -> (projectId, previewId) -> null)
            .withBean(ApprovalCreationService.ApprovalStore.class,
                () -> (context, issued) -> issued);

        runner.run(context -> {
            assertNotNull(context.getBean(ApprovalCreationService.class));
            assertFalse(context.containsBean("configurationDownloadService"));
        });
        runner.withBean(ConfigurationDownloadService.TrustedDownloadReader.class,
                () -> (projectId, approvalId) -> null)
            .withBean(ConfigurationDownloadService.HistoryStore.class,
                () -> (context, history) -> history.id())
            .run(context -> {
                assertNotNull(context.getBean(ApprovalCreationService.class));
                assertNotNull(context.getBean(ConfigurationDownloadService.class));
            });
    }

    private ApplicationContextRunner configured(String hash) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CatalogSourceConfiguration.class,
                FinalizationWorkflowConfiguration.class))
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash);
    }
}
