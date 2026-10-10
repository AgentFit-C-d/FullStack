package com.agentfit.coreapi.catalog;

import java.util.Objects;

/** B's Catalog read boundary; A provides a session-checked caller before any release access. */
public final class AuthenticatedCatalogQueryService {
    @FunctionalInterface
    public interface AuthenticatedCaller {
        String requireUserId();
    }

    private final AuthenticatedCaller caller;
    private final RecommendationCreationService.ApprovedCatalogReader catalogs;

    public AuthenticatedCatalogQueryService(AuthenticatedCaller caller,
                                            RecommendationCreationService.ApprovedCatalogReader catalogs) {
        this.caller = Objects.requireNonNull(caller);
        this.catalogs = Objects.requireNonNull(catalogs);
    }

    public CatalogReadService.Snapshot list(CatalogReadService.Filter filter) {
        String userId = caller.requireUserId();
        if (userId == null || userId.isBlank()) {
            throw new UnauthenticatedException();
        }
        Objects.requireNonNull(filter, "Catalog filter required");
        var approved = Objects.requireNonNull(catalogs.current());
        return CatalogReadService.list(approved.directory(), approved.approvedHash(), filter);
    }

    public static final class UnauthenticatedException extends RuntimeException {
        public UnauthenticatedException() { super("authentication required"); }
    }
}
