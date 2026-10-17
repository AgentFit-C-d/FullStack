package com.agentfit.coreapi.domain.project.service;

@FunctionalInterface
public interface ProjectBDeletionPort {
    void delete(String projectId);

    static ProjectBDeletionPort noop() {
        return projectId -> { };
    }
}
