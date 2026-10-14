package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import java.time.Instant; import java.util.*;
import org.junit.jupiter.api.Test; import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ProjectCreationIdempotencyTest {
 @Test void sameKeyReturnsOriginalProjectAndDifferentPayloadConflicts(){ var projects=new Projects(); var keys=new Keys(); var service=new ProjectService(projects,new ProjectOwnershipGuard(projects),new AuditRecorder(),new ProjectRuntimeRepository(){},new PlanningInputRepository(){},keys,new PlanningInputValidator(),new AnalysisAttemptRepository(){}); var user=new AuthenticatedPrincipal(1,"u",null); var file=new MockMultipartFile("file","p.md","text/markdown","hello".getBytes()); var first=service.create(user,"demo","BACKEND","Java",file,null,"same-key"); var second=service.create(user,"demo","BACKEND","Java",file,null,"same-key"); assertEquals(first.id(),second.id()); assertEquals(1,projects.values.size()); assertThrows(com.agentfit.coreapi.domain.project.exception.IdempotencyKeyReusedException.class,()->service.create(user,"other","BACKEND","Java",file,null,"same-key")); }
 static class Projects implements ProjectRepository { List<Project> values=new ArrayList<>(); public Project save(Project p){values.add(p);return p;} public List<Project> findByOwnerId(long o){return values;} public Optional<Project> findByIdAndOwnerId(String i,long o){return values.stream().filter(p->p.id().equals(i)&&p.ownerId()==o).findFirst();} }
 static class Keys implements ProjectCreationIdempotencyRepository { Map<String,ProjectCreationIdempotency> map=new HashMap<>(); public Optional<ProjectCreationIdempotency> find(long o,String op,String k){return Optional.ofNullable(map.get(o+":"+op+":"+k));} public void reserve(ProjectCreationIdempotency v){map.put(v.ownerId()+":"+v.operation()+":"+v.idempotencyKey(),v);} public void linkProject(long o,String op,String k,String p){var old=map.get(o+":"+op+":"+k);map.put(o+":"+op+":"+k,new ProjectCreationIdempotency(old.ownerId(),old.operation(),old.idempotencyKey(),old.requestFingerprint(),p,old.createdAt()));} }
}
