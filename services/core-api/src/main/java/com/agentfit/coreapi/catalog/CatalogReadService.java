package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.catalog.model.CatalogTool;
import com.agentfit.coreapi.catalog.model.CatalogValidator;
import com.agentfit.coreapi.catalog.model.ToolSupport;
import com.agentfit.coreapi.recommendation.CapabilityKey;
import com.agentfit.coreapi.recommendation.selection.PermissionMapping;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Read-only Catalog projection for a future authenticated API. Discovery candidates are not a source. */
public final class CatalogReadService {
    private CatalogReadService() {}

    public record Filter(String osFamily, String clientId, String clientVersion,
                         String capabilityKey) {
        public Filter(String osFamily, String clientId, String capabilityKey) {
            this(osFamily, clientId, null, capabilityKey);
        }

        public Filter {
            if (osFamily != null && osFamily.isBlank()
                || clientId != null && clientId.isBlank()
                || clientVersion != null && clientVersion.isBlank()
                || capabilityKey != null && capabilityKey.isBlank()
                || capabilityKey != null && !CapabilityKey.keys().contains(capabilityKey)) {
                throw new IllegalArgumentException("invalid Catalog filter");
            }
        }
    }

    public record Support(String key, String osFamily, String clientId, String clientVersion,
                          ToolSupport.Check documentation, ToolSupport.Check format,
                          ToolSupport.Check standalone, boolean verified, VerificationEvidence evidence) {}

    public record Permission(PermissionMapping mapping, VerificationEvidence evidence) {}

    public record Item(String key, String version, Set<String> capabilityKeys,
                       Set<String> dependencyKeys, Set<String> conflictKeys,
                       Set<String> includedComponentKeys, List<Support> support,
                       List<Permission> permissions) {
        public Item {
            capabilityKeys = Set.copyOf(capabilityKeys);
            dependencyKeys = Set.copyOf(dependencyKeys);
            conflictKeys = Set.copyOf(conflictKeys);
            includedComponentKeys = Set.copyOf(includedComponentKeys);
            support = List.copyOf(support);
            permissions = List.copyOf(permissions);
        }
    }

    public record Snapshot(String releaseId, String catalogHash, List<Item> items) {
        public Snapshot { items = List.copyOf(items); }
    }

    public static Snapshot list(Path releaseDirectory, String approvedCatalogHash, Filter filter) {
        return list(CatalogSemanticParser.load(releaseDirectory), approvedCatalogHash, filter);
    }

    static Snapshot list(ParsedCatalog parsed, String approvedCatalogHash, Filter filter) {
        if (parsed == null || filter == null || parsed.catalogHash() == null
            || approvedCatalogHash == null || !approvedCatalogHash.matches("[0-9a-f]{64}")
            || !MessageDigest.isEqual(parsed.catalogHash().getBytes(StandardCharsets.US_ASCII),
                approvedCatalogHash.getBytes(StandardCharsets.US_ASCII))) {
            throw new CatalogBundleLoader.CatalogUnavailableException("catalog hash is not approved");
        }
        CatalogValidator.validate(parsed.release());
        List<Item> items = new ArrayList<>();
        for (CatalogTool tool : parsed.release().tools().values().stream()
            .sorted(Comparator.comparing(CatalogTool::key)).toList()) {
            if (filter.capabilityKey() != null
                && !tool.capabilityKeys().contains(filter.capabilityKey())) continue;
            List<Support> support = new ArrayList<>();
            for (ToolSupport row : tool.support().stream()
                .sorted(Comparator.comparing(ToolSupport::osFamily)
                    .thenComparing(ToolSupport::clientId)
                    .thenComparing(ToolSupport::clientVersion)).toList()) {
                if (filter.osFamily() != null && !filter.osFamily().equals(row.osFamily())
                    || filter.clientId() != null && !filter.clientId().equals(row.clientId())
                    || filter.clientVersion() != null
                        && !filter.clientVersion().equals(row.clientVersion())) continue;
                var evidence = parsed.evidence().get("support:" + row.key());
                if (evidence == null) throw unavailable("support evidence missing");
                boolean verified = row.documentation() == ToolSupport.Check.PASS
                    && row.format() == ToolSupport.Check.PASS
                    && row.standalone() == ToolSupport.Check.PASS;
                support.add(new Support(row.key(), row.osFamily(), row.clientId(),
                    row.clientVersion(), row.documentation(), row.format(), row.standalone(),
                    verified, evidence));
            }
            if ((filter.osFamily() != null || filter.clientId() != null
                || filter.clientVersion() != null) && support.isEmpty()) continue;
            List<Permission> permissions = new ArrayList<>();
            for (PermissionMapping mapping : parsed.permissionMappings().stream()
                .filter(row -> row.toolKey().equals(tool.key()))
                .sorted(Comparator.comparing(PermissionMapping::mappingKey)).toList()) {
                var evidence = parsed.evidence().get("permission:" + tool.key() + ":" + mapping.mappingKey());
                if (evidence == null) throw unavailable("permission evidence missing");
                permissions.add(new Permission(mapping, evidence));
            }
            items.add(new Item(tool.key(), tool.version(), tool.capabilityKeys(),
                tool.dependencyKeys(), tool.conflictKeys(), tool.includedComponentKeys(),
                support, permissions));
        }
        return new Snapshot(parsed.release().releaseId(), parsed.catalogHash(), items);
    }

    private static CatalogBundleLoader.CatalogUnavailableException unavailable(String message) {
        return new CatalogBundleLoader.CatalogUnavailableException(message);
    }
}
