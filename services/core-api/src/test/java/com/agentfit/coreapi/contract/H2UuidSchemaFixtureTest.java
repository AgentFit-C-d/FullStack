package com.agentfit.coreapi.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.DriverManager;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class H2UuidSchemaFixtureTest {
    @Test
    void h2FixtureCreatesCanonicalPhaseTwoTables() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:fixture;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;INIT=RUNSCRIPT FROM 'classpath:schema-h2.sql'", "sa", "")) {
            var tables = Set.of("USER", "PROJECT", "PROJECT_PROFILE", "RECOMMENDATION", "CONFIG_PREVIEW", "AUDIT_EVENT");
            var actual = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"});
            var names = new java.util.HashSet<String>();
            while (actual.next()) names.add(actual.getString("TABLE_NAME"));
            assertEquals(true, names.containsAll(tables));

            try (var columns = connection.getMetaData().getColumns(null, null, "PROJECT", "ID")) {
                columns.next();
                assertEquals("UUID", columns.getString("TYPE_NAME"));
            }
        }
    }
}
