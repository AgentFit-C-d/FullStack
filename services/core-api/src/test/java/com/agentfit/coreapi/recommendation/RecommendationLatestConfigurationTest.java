package com.agentfit.coreapi.recommendation;

import com.agentfit.autoconfigure.RecommendationLatestConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.*;

class RecommendationLatestConfigurationTest {
    @Test
    void latestRecommendationReadRequiresBothOwnerCheckedPorts() {
        var runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RecommendationLatestConfiguration.class))
            .withBean(RecommendationLatestService.OwnerCheckedBasisReader.class,
                () -> projectId -> null);

        runner.run(context -> assertFalse(context.containsBean("recommendationLatestService")));
        runner.withBean(RecommendationLatestService.StoredRecommendationReader.class,
                () -> projectId -> null)
            .run(context -> assertNotNull(context.getBean(RecommendationLatestService.class)));
    }
}
