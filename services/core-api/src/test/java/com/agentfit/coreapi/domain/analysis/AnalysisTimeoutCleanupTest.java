package com.agentfit.coreapi.domain.analysis;
import com.agentfit.coreapi.domain.analysis.entity.*; import com.agentfit.coreapi.domain.analysis.repository.*; import com.agentfit.coreapi.domain.analysis.service.*; import com.agentfit.coreapi.domain.project.repository.*; import com.agentfit.coreapi.domain.project.entity.*; import java.time.*; import java.util.*; import org.junit.jupiter.api.Test; import static org.junit.jupiter.api.Assertions.*;
class AnalysisTimeoutCleanupTest {
 @Test void expiredAttemptTransitionsAndScrubsInput(){ Instant now=Instant.parse("2026-01-01T00:10:00Z"); var repo=new Attempts(); var input=new Inputs(); var admission=new Admission(); var svc=new ProjectAnalysisService(new ProjectRepository(){ public Project save(Project p){return p;} public List<Project> findByOwnerId(long o){return List.of();} public Optional<Project> findByIdAndOwnerId(String i,long o){return Optional.empty();} },repo,admission,input,Clock.fixed(now,ZoneOffset.UTC),600); var a=new AnalysisAttempt(1,"p","r",1,"1","PROCESSING",now.minusSeconds(1),now.minusSeconds(601),null,1L,1L,null); repo.expired=List.of(a); svc.timeout(a); assertEquals("FAILED",repo.state); assertEquals(1,input.scrubbed); assertEquals(0,admission.count); }
 static class Attempts implements AnalysisAttemptRepository { List<AnalysisAttempt> expired=List.of(); String state="PROCESSING"; public List<AnalysisAttempt> findExpired(Instant n){return expired;} public boolean transitionToTerminal(long id,String s,String e,Instant t){state=s;return true;} }
 static class Inputs implements PlanningInputRepository { int scrubbed; public void scrub(String p,long r){scrubbed++;} }
 static class Admission implements AnalysisAdmissionRepository { short count=1; public AnalysisAdmission lock(){return new AnalysisAdmission((short)1,count,Instant.now());} public void decrement(){count--;}}

 @Test void schedulerProcessesExpiredAttemptOnceAndValidatesInterval(){
   var attempts = new SchedulerAttempts();
   var service = org.mockito.Mockito.mock(ProjectAnalysisService.class);
   var now = Instant.parse("2026-01-01T00:10:00Z");
   var attempt = new AnalysisAttempt(2,"p","r2",1,"1","PROCESSING",now.minusSeconds(1),now.minusSeconds(601),null,1L,1L,null);
   attempts.expired = List.of(attempt);
   var scheduler = new AnalysisTimeoutScheduler(attempts, service, Clock.fixed(now, ZoneOffset.UTC), 10);
   scheduler.cleanup();
   org.mockito.Mockito.verify(service).timeout(attempt);
   attempts.expired = List.of();
   scheduler.cleanup();
   org.mockito.Mockito.verifyNoMoreInteractions(service);
   assertThrows(IllegalArgumentException.class, () -> new AnalysisTimeoutScheduler(attempts, service, Clock.fixed(now, ZoneOffset.UTC), 11));
 }

 static class SchedulerAttempts implements AnalysisAttemptRepository { List<AnalysisAttempt> expired=List.of(); public List<AnalysisAttempt> findExpired(Instant n){return expired;} }
}
