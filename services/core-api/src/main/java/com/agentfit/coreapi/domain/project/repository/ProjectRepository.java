package com.agentfit.coreapi.domain.project.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.project.dto.request.*;
import com.agentfit.coreapi.domain.project.dto.response.*;
import com.agentfit.coreapi.domain.project.entity.*;
import com.agentfit.coreapi.domain.project.exception.*;
import com.agentfit.coreapi.domain.project.repository.*;
import com.agentfit.coreapi.domain.project.service.*;
import com.agentfit.coreapi.global.exception.*;
import java.util.List;
import java.util.Optional;

public interface ProjectRepository {
    Project save(Project project);

    List<Project> findByOwnerId(long ownerId);

    Optional<Project> findByIdAndOwnerId(String id, long ownerId);

    default void deleteById(String id) {
        throw new UnsupportedOperationException("deleteById is not implemented");
    }
}



