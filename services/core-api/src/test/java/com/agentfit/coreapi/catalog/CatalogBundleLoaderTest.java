package com.agentfit.coreapi.catalog;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogBundleLoaderTest {
    private static final String FILE_HASH = "44136fa355b3678a1146ad16f7e8649e94fb4fc21fe77e8310c060f61caaff8a";
    private static final String CATALOG_HASH = "38e92a97ab9126a133c5f3bb85ebfdd2d55e43c82515d887b6ded60b6698d3a5";
    private static final List<String> PATHS = List.of("capabilities.json", "client-capabilities.json",
        "permissions.json", "relations.json", "support-matrix.json", "tools.json");

    @TempDir Path directory;

    private void fixture(List<String> manifestOrder) throws IOException {
        for (String name : PATHS) Files.writeString(directory.resolve(name), "{}");
        List<String> entries = new ArrayList<>();
        for (String path : manifestOrder) {
            entries.add("{\"path\":\"" + path + "\",\"sha256\":\"" + FILE_HASH + "\"}");
        }
        Files.writeString(directory.resolve("manifest.json"),
            "{\"schemaVersion\":1,\"releaseId\":\"release-test-1\",\"catalogHash\":\""
                + CATALOG_HASH + "\",\"files\":[" + String.join(",", entries) + "]}");
    }

    @Test
    void loadsOnlyVerifiedBytesWithStableHashRegardlessOfManifestOrder() throws IOException {
        fixture(PATHS);
        VerifiedCatalogBundle bundle = CatalogBundleLoader.load(directory);
        assertEquals("release-test-1", bundle.releaseId());
        assertEquals(CATALOG_HASH, bundle.catalogHash());
        assertEquals("{}", bundle.files().get("tools.json"));
        assertEquals(6, bundle.files().size());
        assertThrows(UnsupportedOperationException.class, () -> bundle.files().put("extra", "{}"));

        List<String> reversed = new ArrayList<>(PATHS);
        Collections.reverse(reversed);
        fixture(reversed);
        assertEquals(CATALOG_HASH, CatalogBundleLoader.load(directory).catalogHash());
    }

    @Test
    void rejectsChangedMissingOrUnlistedFiles() throws IOException {
        fixture(PATHS);
        Files.writeString(directory.resolve("tools.json"), "{\"changed\":true}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));

        fixture(PATHS);
        Files.delete(directory.resolve("tools.json"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));

        fixture(PATHS);
        Files.writeString(directory.resolve("unlisted.json"), "{}");
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));
    }

    @Test
    void rejectsDuplicateTraversalAndIncorrectReleaseHash() throws IOException {
        List<String> duplicate = new ArrayList<>(PATHS);
        duplicate.add("tools.json");
        fixture(duplicate);
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));

        fixture(PATHS);
        String manifest = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"), manifest.replace("tools.json", "../tools.json"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));

        fixture(PATHS);
        manifest = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"), manifest.replace(CATALOG_HASH, FILE_HASH));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));
    }

    @Test
    void schemaVersionMustBeAnIntegerOne() throws IOException {
        fixture(PATHS);
        String manifest = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"),
            manifest.replace("\"schemaVersion\":1", "\"schemaVersion\":1.5"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));

        fixture(PATHS);
        manifest = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"),
            manifest.replace("\"schemaVersion\":1", "\"schemaVersion\":4294967297"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));
    }

    @Test
    void rejectsDuplicateManifestKeys() throws IOException {
        fixture(PATHS);
        String manifest = Files.readString(directory.resolve("manifest.json"));
        Files.writeString(directory.resolve("manifest.json"),
            manifest.replace("\"schemaVersion\":1", "\"schemaVersion\":2,\"schemaVersion\":1"));
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));
    }

    @Test
    void rejectsSymlinkedFileWhenPlatformAllowsCreatingOne() throws IOException {
        fixture(PATHS);
        Path target = directory.resolve("real-tools.json");
        Files.writeString(target, "{}");
        Files.delete(directory.resolve("tools.json"));
        try {
            Files.createSymbolicLink(directory.resolve("tools.json"), target);
        } catch (UnsupportedOperationException | IOException | SecurityException exception) {
            Assumptions.abort("symlink creation unavailable on this workstation");
        }
        assertThrows(CatalogBundleLoader.CatalogUnavailableException.class,
            () -> CatalogBundleLoader.load(directory));
    }
}
