package com.agentfit.coreapi.domain.auth.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
    Optional<UserAccount> findByGithubUserId(long githubUserId);

    Optional<UserAccount> findById(long id);

    default Optional<UserAccount> findByUuid(UUID id) {
        return Optional.empty();
    }

    UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName);
}



