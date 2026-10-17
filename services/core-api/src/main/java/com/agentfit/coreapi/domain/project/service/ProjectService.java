package com.agentfit.coreapi.domain.project.service;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import com.agentfit.coreapi.domain.audit.service.AuditRecorder;
import com.agentfit.coreapi.domain.analysis.repository.AnalysisAttemptRepository;
import com.agentfit.coreapi.domain.analysis.dto.response.AnalysisAttemptResponse;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Locale;

import com.agentfit.coreapi.domain.auth.entity.AuthenticatedPrincipal;
import com.agentfit.coreapi.domain.project.exception.ProjectNotFoundException;

@Service
public class ProjectService {
    private final ProjectRepository repository;
    private final ProjectOwnershipGuard ownershipGuard;
    private final AuditRecorder auditRecorder;
    private final ProjectRuntimeRepository runtimeRepository;
    private final PlanningInputRepository inputRepository;
    private final ProjectCreationIdempotencyRepository idempotencyRepository;
    private final PlanningInputValidator inputValidator;
    private final AnalysisAttemptRepository analysisAttemptRepository;
    private final ProjectBDeletionPort bDeletion;

    public ProjectService(ProjectRepository repository) {
        this(repository, new ProjectOwnershipGuard(repository), new com.agentfit.coreapi.domain.audit.service.AuditRecorder(), new ProjectRuntimeRepository(){}, new PlanningInputRepository(){}, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), new AnalysisAttemptRepository(){}, ProjectBDeletionPort.noop());
    }

    public ProjectService(ProjectRepository repository,
            ProjectOwnershipGuard ownershipGuard,
            AuditRecorder auditRecorder) {
        this(repository, ownershipGuard, auditRecorder, new ProjectRuntimeRepository(){}, new PlanningInputRepository(){}, new ProjectCreationIdempotencyRepository(){}, new PlanningInputValidator(), new AnalysisAttemptRepository(){}, ProjectBDeletionPort.noop());
    }

    public ProjectService(ProjectRepository repository, ProjectOwnershipGuard ownershipGuard, AuditRecorder auditRecorder,
            ProjectRuntimeRepository runtimeRepository, PlanningInputRepository inputRepository,
            ProjectCreationIdempotencyRepository idempotencyRepository, PlanningInputValidator inputValidator,
            AnalysisAttemptRepository analysisAttemptRepository) {
        this(repository, ownershipGuard, auditRecorder, runtimeRepository, inputRepository,
            idempotencyRepository, inputValidator, analysisAttemptRepository, ProjectBDeletionPort.noop());
    }

    @Autowired
    public ProjectService(ProjectRepository repository, ProjectOwnershipGuard ownershipGuard, AuditRecorder auditRecorder,
            ProjectRuntimeRepository runtimeRepository, PlanningInputRepository inputRepository,
            ProjectCreationIdempotencyRepository idempotencyRepository, PlanningInputValidator inputValidator,
            AnalysisAttemptRepository analysisAttemptRepository, ProjectBDeletionPort bDeletion) {
        this.repository = repository;
        this.ownershipGuard = ownershipGuard;
        this.auditRecorder = auditRecorder;
        this.runtimeRepository = runtimeRepository;
        this.inputRepository = inputRepository;
        this.idempotencyRepository = idempotencyRepository;
        this.inputValidator = inputValidator;
        this.analysisAttemptRepository = analysisAttemptRepository;
        this.bDeletion = bDeletion;
    }

    @Transactional
    public Project create(AuthenticatedPrincipal principal, String rawName) {
        if (principal == null) {
            throw new com.agentfit.coreapi.global.exception.UnauthenticatedException();
        }
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.codePointCount(0, name.length()) > 100) {
            throw new IllegalArgumentException("Project name must contain 1 to 100 code points");
        }
        Instant now = Instant.now();
        Project project = new Project(java.util.UUID.randomUUID().toString(), principal.userId(), principal.userUuid(), name, 1L, now, now);
        Project saved = repository.save(project);
        recordProjectAudit(principal, saved.id(), "PROJECT_CREATED", now);
        return saved;
    }

    @Transactional
    public Project create(AuthenticatedPrincipal principal, String rawName, String role, String rawRuntimes,
            MultipartFile file, String inputText, String idempotencyKey) {
        if (principal == null) throw new UnauthenticatedException();
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) throw new IllegalArgumentException("Idempotency-Key is required");
        String name = rawName == null ? "" : rawName.trim();
        if (name.isEmpty() || name.codePointCount(0,name.length()) > 100) throw new IllegalArgumentException("Project name must contain 1 to 100 code points");
        String normalizedRole = role == null || role.isBlank() ? "BACKEND" : role.trim().toUpperCase(Locale.ROOT);
        try { com.agentfit.coreapi.domain.project.entity.DeveloperRole.valueOf(normalizedRole); } catch (IllegalArgumentException e) { throw new IllegalArgumentException("Invalid role"); }
        List<String> runtimes = parseRuntimes(rawRuntimes);
        String fingerprint = fingerprint(name, normalizedRole, runtimes, file, inputText);
        var existing = principal.userUuid() == null
            ? idempotencyRepository.find(principal.userId(), "PROJECT_CREATE", idempotencyKey)
            : idempotencyRepository.findByOwnerUuid(principal.userUuid(), "PROJECT_CREATE", idempotencyKey);
        if (existing.isPresent()) {
            if (!existing.get().requestFingerprint().equals(fingerprint)) throw new com.agentfit.coreapi.domain.project.exception.IdempotencyKeyReusedException();
            if (existing.get().projectId() != null) return ownershipGuard.requireOwned(principal, existing.get().projectId());
        }
        java.time.Instant now = Instant.now();
        String id = java.util.UUID.randomUUID().toString();
        if (existing.isEmpty()) {
            boolean reserved = principal.userUuid() == null
                ? idempotencyRepository.reserveIfAbsent(new com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency(principal.userId(),"PROJECT_CREATE",idempotencyKey,fingerprint,null,now))
                : idempotencyRepository.reserveIfAbsentByOwnerUuid(new com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency(principal.userUuid(),"PROJECT_CREATE",idempotencyKey,fingerprint,null,now));
            if (!reserved) {
                java.util.Optional<com.agentfit.coreapi.domain.project.entity.ProjectCreationIdempotency> concurrent = principal.userUuid() == null
                    ? idempotencyRepository.find(principal.userId(), "PROJECT_CREATE", idempotencyKey)
                    : idempotencyRepository.findByOwnerUuid(principal.userUuid(), "PROJECT_CREATE", idempotencyKey);
                var reservedRequest = concurrent.orElseThrow(() -> new IllegalStateException("Idempotency reservation was not visible"));
                if (!reservedRequest.requestFingerprint().equals(fingerprint)) throw new com.agentfit.coreapi.domain.project.exception.IdempotencyKeyReusedException();
                if (reservedRequest.projectId() != null) return ownershipGuard.requireOwned(principal, reservedRequest.projectId());
                throw new IllegalStateException("Idempotency request is still being created");
            }
        }
        Project project = new Project(id, principal.userId(), principal.userUuid(), name, 1L, now, now)
            .withLegacyState("CREATED", normalizedRole, 1L);
        PlanningInput input = inputValidator.validate(id, 1L, file, inputText);
        repository.save(project); runtimeRepository.replace(id, toRuntimes(id, runtimes)); inputRepository.save(input);
        if (principal.userUuid() == null) idempotencyRepository.linkProject(principal.userId(),"PROJECT_CREATE",idempotencyKey,id);
        else idempotencyRepository.linkProjectByOwnerUuid(principal.userUuid(), "PROJECT_CREATE", idempotencyKey, id);
        recordProjectAudit(principal, id, "PROJECT_CREATED", now);
        return project;
    }

    private List<String> parseRuntimes(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        List<String> out = new ArrayList<>();
        for (String token : raw.split("[,\\r\\n]+")) { String v=token.trim(); if(v.isBlank()) continue; if(v.codePointCount(0,v.length())>200) throw new IllegalArgumentException("Runtime item exceeds 200 code points"); String key=v.toLowerCase(Locale.ROOT); if(out.stream().noneMatch(x->x.toLowerCase(Locale.ROOT).equals(key))) out.add(v); }
        if(out.size()>30) throw new IllegalArgumentException("At most 30 runtime items are allowed"); return out;
    }
    private List<com.agentfit.coreapi.domain.project.entity.ProjectRuntime> toRuntimes(String id,List<String> values){ List<com.agentfit.coreapi.domain.project.entity.ProjectRuntime> out=new ArrayList<>(); for(int i=0;i<values.size();i++) out.add(new com.agentfit.coreapi.domain.project.entity.ProjectRuntime(id,i+1,values.get(i),values.get(i).toLowerCase(Locale.ROOT))); return out; }
    private String fingerprint(String name,String role,List<String> runtimes,MultipartFile file,String text){ try { MessageDigest md=MessageDigest.getInstance("SHA-256"); md.update(name.getBytes(StandardCharsets.UTF_8)); md.update((byte)0); md.update(role.getBytes(StandardCharsets.UTF_8)); md.update((byte)0); runtimes.forEach(v->{md.update(v.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));md.update((byte)0);}); if(file!=null&&!file.isEmpty()) md.update(file.getBytes()); else if(text!=null) md.update(text.getBytes(StandardCharsets.UTF_8)); return java.util.HexFormat.of().formatHex(md.digest()); } catch(Exception e){throw new IllegalStateException(e);} }

    @Transactional
    public Project update(AuthenticatedPrincipal principal, String projectId, ProjectUpdateRequest request) {
        Project current = ownershipGuard.requireOwned(principal, projectId);
        Project locked = repository.findByIdForUpdate(projectId).orElse(current);
        if (locked.version() != request.expectedVersion()) throw new VersionConflictException(request.expectedVersion(), locked.version());
        String name = request.name() == null ? locked.name() : request.name().trim();
        if(name.isBlank() || name.codePointCount(0,name.length())>100) throw new IllegalArgumentException("Project name must contain 1 to 100 code points");
        String role = request.role()==null?locked.developerRole():request.role().trim().toUpperCase(Locale.ROOT);
        try { DeveloperRole.valueOf(role); } catch(Exception e){ throw new IllegalArgumentException("Invalid role"); }
        List<String> runtimes = request.runtimes() == null
                ? runtimeRepository.findByProjectId(projectId).stream()
                    .sorted(java.util.Comparator.comparingInt(ProjectRuntime::ordinal))
                    .map(ProjectRuntime::value).toList()
                : parseRuntimes(request.runtimes());
        long version=locked.version()+1; Instant now=Instant.now(); repository.updateMetadata(projectId,name,role,version,now); runtimeRepository.replace(projectId,toRuntimes(projectId,runtimes));
        return new Project(projectId,locked.ownerId(),name,version,locked.stage(),locked.analysisGeneration(),locked.reviewGeneration(),locked.createdAt(),now,role,locked.currentInputRevision(),locked.ownerUuid());
    }

    @Transactional
    public PlanningInputMetadataResponse replaceInput(AuthenticatedPrincipal principal, String projectId, long expectedVersion, MultipartFile file, String inputText) {
        Project current=ownershipGuard.requireOwned(principal,projectId); Project locked=repository.findByIdForUpdate(projectId).orElse(current);
        if(locked.version()!=expectedVersion) throw new VersionConflictException(expectedVersion,locked.version());
        if(analysisAttemptRepository.findProcessingByProjectId(projectId).isPresent()) throw new IllegalStateException("Cannot replace input while analysis is processing");
        long revision=(locked.currentInputRevision()==null?0:locked.currentInputRevision())+1; PlanningInput input=inputValidator.validate(projectId,revision,file,inputText); inputRepository.save(input); repository.updateInputRevision(projectId,revision,locked.version()+1,Instant.now()); return PlanningInputMetadataResponse.from(input);
    }

    @Transactional(readOnly = true)
    public List<Project> list(AuthenticatedPrincipal principal) {
        if (principal != null && principal.userUuid() != null) {
            return repository.findByOwnerUuid(principal.userUuid());
        }
        return repository.findByOwnerId(principal.userId());
    }

    @Transactional(readOnly = true)
    public ProjectResponse response(Project project) {
        List<String> runtimes = runtimeRepository.findByProjectId(project.id()).stream()
                .sorted(java.util.Comparator.comparingInt(ProjectRuntime::ordinal))
                .map(ProjectRuntime::value)
                .toList();
        return ProjectResponse.from(project, runtimes);
    }

    @Transactional(readOnly = true)
    public Project get(AuthenticatedPrincipal principal, String projectId) {
        return ownershipGuard.requireOwned(principal, projectId);
    }

    @Transactional(readOnly = true)
    public ProjectDetailResponse detail(AuthenticatedPrincipal principal, String projectId) {
        Project project = ownershipGuard.requireOwned(principal, projectId);
        var input = inputRepository.findCurrent(projectId).map(PlanningInputMetadataResponse::from).orElse(null);
        var attempt = analysisAttemptRepository.findLatestByProjectId(projectId).map(AnalysisAttemptResponse::from).orElse(null);
        return new ProjectDetailResponse(response(project), input, null, attempt);
    }

    @Transactional
    public void delete(AuthenticatedPrincipal principal, String projectId,
            boolean confirmation, long expectedVersion) {
        if (!confirmation) {
            throw new IllegalArgumentException("confirmation must be true");
        }
        Project project = ownershipGuard.requireOwned(principal, projectId);
        Project locked = repository.findByIdForUpdate(projectId).orElse(project);
        if (analysisAttemptRepository.findProcessingByProjectId(projectId).isPresent()) throw new ActiveAnalysisProjectDeletionException();
        if (locked.version() != expectedVersion) {
            throw new VersionConflictException(expectedVersion, locked.version());
        }
        bDeletion.delete(locked.id());
        auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
            principal.userId(), locked.id(), "PROJECT_DELETED", "SUCCEEDED", Instant.now(), null,
            java.util.Map.of("target", "PROJECT")));
        repository.deleteProjectGraph(locked.id());
    }

    private void recordProjectAudit(AuthenticatedPrincipal principal, String projectId, String action, Instant now) {
        if (principal.userUuid() != null) {
            auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
                principal.userUuid(), java.util.UUID.fromString(projectId), action, "SUCCEEDED", now, null,
                java.util.Map.of("target", "PROJECT")));
            return;
        }
        auditRecorder.record(new com.agentfit.coreapi.domain.audit.entity.AuditEvent(
            principal.userId(), projectId, action, "SUCCEEDED", now, null, java.util.Map.of("target", "PROJECT")));
    }
}



