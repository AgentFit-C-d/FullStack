package com.agentfit.coreapi.domain.auth.repository;

import com.agentfit.coreapi.domain.auth.entity.UserAccount;
import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
    Optional<UserAccount> findByGithubUserId(long githubUserId);

    default Optional<UserAccount> findById(long id) {
        return Optional.empty();
    }

    default Optional<UserAccount> findByUuid(UUID id) {
        return Optional.empty();
    }
    UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName);
}



