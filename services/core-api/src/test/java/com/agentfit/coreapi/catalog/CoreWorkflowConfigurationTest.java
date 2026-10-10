package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.CoreApiApplication;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import com.agentfit.coreapi.recommendation.ai.AiCapabilityIntake;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class CoreWorkflowConfigurationTest {
    @TempDir Path directory;

    @Test
    void neitherWorkflowActivatesWithOnlyTheApprovedCatalog() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        new ApplicationContextRunner()
            .withUserConfiguration(CatalogSourceConfiguration.class, CoreWorkflowConfiguration.class)
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .run(context -> {
                assertFalse(context.containsBean("recommendationCreationService"));
                assertFalse(context.containsBean("previewCreationService"));
            });
    }

    @Test
    void recommendationActivatesOnlyWithOwnerSnapshotAiAndAtomicStore() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogSourceConfiguration.class, CoreWorkflowConfiguration.class)
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .withBean(RecommendationCreationService.OwnerCheckedSnapshotReader.class,
                () -> projectId -> new RecommendationCreationService.OwnerCheckedSnapshot(
                    projectId, 1, "profile-1", 1, "event-1", 0, 0, 1,
                    Set.of("profile.features"), Map.of("profile.features", "document search"),
                    false, new EnvironmentTarget("WINDOWS", "example-client", "1.0"), Map.of()))
            .withBean(RecommendationCreationService.CapabilityAnalyzer.class,
                () -> snapshot -> new RecommendationCreationService.CapabilityAssessment(
                    CapabilityKey.keys().stream().sorted().map(key ->
                        new AiCapabilityIntake.Claim(key,
                            key.equals("cap_document_reference") ? AiCapabilityIntake.Need.REQUIRED
                                : AiCapabilityIntake.Need.OPTIONAL,
                            "Confirmed profile", List.of("profile.features"))).toList(), List.of()));

        runner.run(context -> assertFalse(context.containsBean("recommendationCreationService")));
        runner.withBean(RecommendationCreationService.RecommendationStore.class,
                () -> (snapshot, basis, result) -> "rec-1")
            .run(context -> {
                var created = context.getBean(RecommendationCreationService.class).create("project-1");
                assertEquals("rec-1", created.recommendationId());
                assertEquals(List.of("example-tool"), created.result().decision().toolKeys());
            });
    }

    @Test
    void previewActivatesOnlyWithTrustedContextAndAtomicStore() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        var runner = new ApplicationContextRunner()
            .withUserConfiguration(CatalogSourceConfiguration.class, CoreWorkflowConfiguration.class)
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .withBean(PreviewCreationService.TrustedContextReader.class,
                () -> (projectId, recommendationId) -> null);

        runner.run(context -> assertFalse(context.containsBean("previewCreationService")));
        runner.withBean(PreviewCreationService.PreviewStore.class,
                () -> (context, draft) -> null)
            .run(context -> assertNotNull(context.getBean(PreviewCreationService.class)));
    }

    @Test
    void actualApplicationActivatesRecommendationWhenAllTrustedPortsExist() throws Exception {
        String hash = SyntheticCatalogBundle.write(directory);
        new ApplicationContextRunner()
            .withUserConfiguration(CoreApiApplication.class)
            .withPropertyValues("agentfit.catalog.directory=" + directory,
                "agentfit.catalog.approved-hash=" + hash)
            .withBean(RecommendationCreationService.OwnerCheckedSnapshotReader.class,
                () -> projectId -> null)
            .withBean(RecommendationCreationService.CapabilityAnalyzer.class,
                () -> snapshot -> null)
            .withBean(RecommendationCreationService.RecommendationStore.class,
                () -> (snapshot, basis, result) -> null)
            .run(context -> assertNotNull(context.getBean(RecommendationCreationService.class)));
    }
}
