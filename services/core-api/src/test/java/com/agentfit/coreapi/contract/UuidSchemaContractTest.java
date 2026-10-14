package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UuidSchemaContractTest {
    @Test
    void phaseTwoMigrationDeclaresTheCompleteUuidSchema() throws Exception {
        Path migration = Path.of("src/main/resources/db/migration/V5__agentfit_ab_uuid_schema.sql");
        String sql = Files.readString(migration, StandardCharsets.UTF_8).toLowerCase();

        for (String table : new String[] {
            "\"user\"", "project", "project_profile", "project_profile_item",
            "project_profile_gap", "document_summary", "analysis_attempt",
            "developer_profile", "environment_profile", "environment_runtime",
            "environment_auth_readiness", "profile_confirmation_event", "profile_review",
            "profile_review_item", "analysis_failure_diagnostic",
            "environment_installed_component", "recommendation",
            "recommendation_capability", "recommendation_item", "recommendation_notice",
            "config_preview", "config_selection", "config_permission_selection",
            "config_preview_file", "config_export", "audit_event" }) {
            assertTrue(sql.contains("create table " + table), "missing table: " + table);
        }
        assertTrue(sql.contains("id uuid"));
        assertTrue(sql.contains("project_id uuid"));
        assertTrue(sql.contains("drop table if exists"));
    }
}
