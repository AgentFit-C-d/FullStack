package com.agentfit.coreapi.domain.project.dto.response;

public record ProjectDetailResponse(
        ProjectResponse project,
        Object confirmed,
        Object draft,
        Object latestAttempt
) {
}
