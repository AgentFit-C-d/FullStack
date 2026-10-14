package com.agentfit.coreapi.domain.analysis.dto.response;
import com.agentfit.coreapi.domain.analysis.entity.AnalysisAttempt;
import java.time.Instant;
public record AnalysisAttemptResponse(long id,String projectId,String state,Instant deadlineAt,Instant startedAt,String errorCode){ public static AnalysisAttemptResponse from(AnalysisAttempt a){return new AnalysisAttemptResponse(a.id(),a.projectId(),a.state(),a.deadlineAt(),a.startedAt(),a.errorCode());}}
