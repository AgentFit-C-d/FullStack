package com.agentfit.coreapi.domain.analysis.service;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import java.time.Clock; import java.time.Instant;
import org.springframework.scheduling.annotation.Scheduled; import org.springframework.stereotype.Component;
import org.springframework.dao.DataAccessException;
import org.springframework.beans.factory.annotation.Value;
@Component
public class AnalysisTimeoutScheduler {
 private final AnalysisAttemptRepository attempts; private final ProjectAnalysisService service; private final Clock clock; private final long intervalSeconds;
 public AnalysisTimeoutScheduler(AnalysisAttemptRepository a,ProjectAnalysisService s,Clock c,@Value("${agentfit.analysis.cleanup-interval-seconds:10}") long intervalSeconds){attempts=a;service=s;clock=c;if(intervalSeconds<1||intervalSeconds>10) throw new IllegalArgumentException("cleanup interval must be between 1 and 10 seconds");this.intervalSeconds=intervalSeconds;}
 @Scheduled(fixedDelayString="${agentfit.analysis.cleanup-interval-seconds:10}000")
 public void cleanup(){try {for(var a:attempts.findExpired(Instant.now(clock))) service.timeout(a);} catch (DataAccessException ignored) { /* schema may be disabled in web slices */ }}
}
