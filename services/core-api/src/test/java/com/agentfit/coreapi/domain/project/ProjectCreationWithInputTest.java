package com.agentfit.coreapi.domain.project;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.entity.Project;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockMultipartFile;
import static org.junit.jupiter.api.Assertions.*;

class ProjectCreationWithInputTest {
 @Test void storesSingleRoleAndDeduplicatedRuntimeMetadata(){
   var repo=new Repo(); var runtimes=new Runtimes();
   var service=new ProjectService(repo,new ProjectOwnershipGuard(repo),new com.agentfit.coreapi.domain.audit.service.AuditRecorder(),runtimes,new PlanningInputRepository(){},new ProjectCreationIdempotencyRepository(){},new com.agentfit.coreapi.domain.project.service.PlanningInputValidator(),new com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository(){});
   Project p=service.create(new AuthenticatedPrincipal(1,"A",null),"demo","FULL_STACK","Java\njava, Spring",new MockMultipartFile("file","p.md","text/markdown","hello".getBytes()),null,"key-1");
   assertEquals("FULL_STACK",p.developerRole()); assertEquals(1,repo.saved.size());
   assertEquals(java.util.List.of("Java","Spring"),runtimes.values.stream().map(com.agentfit.coreapi.domain.project.entity.ProjectRuntime::value).toList());
   assertEquals(java.util.List.of("Java","Spring"),service.response(p).runtimes());
 }

 @Test void rejectsInvalidInputBeforePersistingProject(){
   var repo=new Repo(); var service=new ProjectService(repo);
   assertThrows(IllegalArgumentException.class,()->service.create(new AuthenticatedPrincipal(1,"A",null),"demo","BACKEND","Java",null,null,"key-2"));
   assertTrue(repo.saved.isEmpty());
 }

 @ParameterizedTest
 @ValueSource(strings = {"FRONTEND", "BACKEND", "FULL_STACK", "AI", "DESIGNER"})
 void acceptsEveryDeclaredRole(String role) {
   var repo=new Repo();
   var service=new ProjectService(repo);
   assertDoesNotThrow(() -> service.create(new AuthenticatedPrincipal(1,"A",null),"demo",role,"Java",new MockMultipartFile("file","p.md","text/markdown","hello".getBytes()),null,"key-"+role));
 }

 @Test void enforcesRuntimeCountAndItemLength() {
   var service=new ProjectService(new Repo());
   String tooMany=java.util.stream.IntStream.range(0,31).mapToObj(i->"r"+i).collect(java.util.stream.Collectors.joining(","));
   var file=new MockMultipartFile("file","p.md","text/markdown","hello".getBytes());
   assertThrows(IllegalArgumentException.class,()->service.create(new AuthenticatedPrincipal(1,"A",null),"demo","BACKEND",tooMany,file,null,"key-many"));
   assertThrows(IllegalArgumentException.class,()->service.create(new AuthenticatedPrincipal(1,"A",null),"demo","BACKEND","x".repeat(201),file,null,"key-long"));
 }
 static class Repo implements ProjectRepository { List<Project> saved=new ArrayList<>(); public Project save(Project p){saved.add(p);return p;} public List<Project> findByOwnerId(long o){return saved;} public Optional<Project> findByIdAndOwnerId(String id,long o){return saved.stream().filter(p->p.id().equals(id)).findFirst();} }
 static class Runtimes implements ProjectRuntimeRepository { List<com.agentfit.coreapi.domain.project.entity.ProjectRuntime> values=new ArrayList<>(); public void replace(String id,List<com.agentfit.coreapi.domain.project.entity.ProjectRuntime> input){values.clear();values.addAll(input);} public List<com.agentfit.coreapi.domain.project.entity.ProjectRuntime> findByProjectId(String id){return values;} }
}
