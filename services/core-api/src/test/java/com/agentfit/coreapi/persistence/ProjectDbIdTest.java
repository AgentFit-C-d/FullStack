package com.agentfit.coreapi.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProjectDbIdTest {
    @Test
    void convertsCanonicalProjectIdToUuidForJdbc() {
        UUID id = UUID.fromString("11111111-1111-4111-8111-111111111111");

        assertEquals(id, ProjectDbId.uuid(id.toString()));
    }

    @Test
    void rejectsLegacyAndMalformedProjectIdsBeforeSql() {
        assertThrows(IllegalArgumentException.class, () -> ProjectDbId.uuid("project-1"));
        assertThrows(IllegalArgumentException.class, () -> ProjectDbId.uuid(""));
        assertThrows(IllegalArgumentException.class, () -> ProjectDbId.uuid(null));
    }
}
