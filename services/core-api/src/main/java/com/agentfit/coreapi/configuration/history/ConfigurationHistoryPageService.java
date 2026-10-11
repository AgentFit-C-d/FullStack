package com.agentfit.coreapi.configuration.history;

import com.agentfit.coreapi.configuration.preview.PreviewBasis;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Projects an A-owned, content-free generation page against one current project basis. */
public final class ConfigurationHistoryPageService {
    public record Cursor(Instant generatedAt, String generationId) {
        public Cursor {
            Objects.requireNonNull(generatedAt);
            if (blank(generationId)) throw new IllegalArgumentException("generation ID required");
        }
    }

    public record StoredItem(ConfigurationGenerationHistory generation,
                             ConfigurationHistoryReadModel.UserReport latestUserReport) {
        public StoredItem { Objects.requireNonNull(generation); }
    }

    public record StoredPage(List<StoredItem> items, Cursor nextCursor) {
        public StoredPage { items = List.copyOf(items); }
    }

    public record Page(List<ConfigurationHistoryReadModel> items, Cursor nextCursor) {
        public Page { items = List.copyOf(items); }
    }

    /** A checks ownership and returns a bounded page from one consistent read transaction. */
    public interface PageReader {
        StoredPage list(String projectId, Cursor after, int limit);
    }

    private final ConfigurationHistoryQueryService.OwnerCheckedBasisReader bases;
    private final PageReader pages;

    public ConfigurationHistoryPageService(
        ConfigurationHistoryQueryService.OwnerCheckedBasisReader bases, PageReader pages) {
        this.bases = Objects.requireNonNull(bases);
        this.pages = Objects.requireNonNull(pages);
    }

    public Page list(String projectId, Cursor after, int limit) {
        if (blank(projectId) || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("invalid generation history page request");
        }
        PreviewBasis current = Objects.requireNonNull(bases.load(projectId));
        if (!projectId.equals(current.projectId())) {
            throw new IllegalArgumentException("current basis belongs to another project");
        }
        StoredPage stored = Objects.requireNonNull(pages.list(projectId, after, limit));
        if (stored.items().size() > limit || (stored.items().isEmpty()
            && stored.nextCursor() != null)) {
            throw new IllegalStateException("invalid generation history page");
        }
        List<ConfigurationHistoryReadModel> result = new ArrayList<>();
        Cursor previous = after;
        for (StoredItem item : stored.items()) {
            ConfigurationGenerationHistory generation = item.generation();
            if (!projectId.equals(generation.basis().projectId())) {
                throw new IllegalArgumentException("generation belongs to another project");
            }
            Cursor position = new Cursor(generation.createdAt(), generation.id());
            if (previous != null && (position.generatedAt().isAfter(previous.generatedAt())
                || position.generatedAt().equals(previous.generatedAt())
                    && position.generationId().compareTo(previous.generationId()) <= 0)) {
                throw new IllegalStateException("generation history page is not ordered");
            }
            result.add(ConfigurationHistoryReadModel.project(generation, current,
                item.latestUserReport()));
            previous = position;
        }
        if (stored.nextCursor() != null && !stored.nextCursor().equals(previous)) {
            throw new IllegalStateException("generation history cursor does not match page");
        }
        return new Page(result, stored.nextCursor());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
