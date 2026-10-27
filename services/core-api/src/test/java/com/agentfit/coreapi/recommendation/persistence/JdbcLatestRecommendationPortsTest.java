package com.agentfit.coreapi.recommendation.persistence;

import com.agentfit.coreapi.catalog.CatalogBundleLoader;
import com.agentfit.coreapi.catalog.RecommendationCreationService;
import com.agentfit.coreapi.recommendation.EnvironmentTarget;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;

import static org.junit.jupiter.api.Assertions.*;

class JdbcLatestRecommendationPortsTest {
    @Test
    void preservesOwnerCheckedSnapshotWhenCatalogCannotBeRead() {
        String projectId = UUID.randomUUID().toString();
        var target = new EnvironmentTarget("WINDOWS", "claude-code", "2.1.270");
        var snapshot = new RecommendationCreationService.OwnerCheckedSnapshot(
            projectId, 2, UUID.randomUUID().toString(), 1, UUID.randomUUID().toString(),
            0, 1, 1, Set.of(), Map.of(), false, target, Map.of());
        var beans = new DefaultListableBeanFactory();
        beans.registerSingleton("unavailableCatalog", (RecommendationCreationService.ApprovedCatalogReader) () -> {
            throw new CatalogBundleLoader.CatalogUnavailableException("release unavailable");
        });
        ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs =
            beans.getBeanProvider(RecommendationCreationService.ApprovedCatalogReader.class);
        var ports = new JdbcLatestRecommendationPorts(id -> snapshot, catalogs, null);

        var current = ports.load(projectId);

        assertFalse(current.catalogAvailable());
        assertEquals(projectId, current.basis().projectId());
        assertEquals(snapshot.projectVersion(), current.basis().projectVersion());
        assertEquals("0".repeat(64), current.basis().catalogHash());
        assertEquals(target, current.target());
    }
}
