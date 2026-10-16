package com.agentfit.coreapi.catalog;

import com.agentfit.coreapi.domain.auth.service.SessionPrincipalResolver;
import com.agentfit.coreapi.global.exception.UnauthenticatedException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Read-only view of independently approved tools; discovery candidates never enter this API. */
@RestController
public class CatalogController {
    public record CatalogResponse(String revision, String catalogHash,
                                  List<CatalogReadService.Item> items) {
        public CatalogResponse { items = List.copyOf(items); }
    }

    private static final Set<String> FILTERS = Set.of("clientId", "osFamily", "capabilityId");
    private final SessionPrincipalResolver principals;
    private final ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs;

    public CatalogController(SessionPrincipalResolver principals,
            ObjectProvider<RecommendationCreationService.ApprovedCatalogReader> catalogs) {
        this.principals = principals;
        this.catalogs = catalogs;
    }

    @GetMapping("/api/tool-catalog")
    public ResponseEntity<CatalogResponse> list(HttpServletRequest request) {
        principals.resolve(request).orElseThrow(UnauthenticatedException::new);
        Map<String, String[]> parameters = request.getParameterMap();
        for (var entry : parameters.entrySet()) {
            if (!FILTERS.contains(entry.getKey()) || entry.getValue() == null
                || entry.getValue().length != 1 || entry.getValue()[0].isBlank()) {
                throw new IllegalArgumentException("invalid Catalog filter");
            }
        }
        var filter = new CatalogReadService.Filter(value(parameters, "osFamily"),
            value(parameters, "clientId"), value(parameters, "capabilityId"));
        var source = catalogs.getIfAvailable();
        if (source == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "approved Catalog is unavailable");
        }
        var approved = source.current();
        var snapshot = CatalogReadService.list(approved.directory(), approved.approvedHash(), filter);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .body(new CatalogResponse(snapshot.releaseId(), snapshot.catalogHash(),
                snapshot.items()));
    }

    private static String value(Map<String, String[]> parameters, String key) {
        String[] found = parameters.get(key);
        return found == null ? null : found[0];
    }
}
