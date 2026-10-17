package com.agentfit.coreapi.domain.auth.repository;


import java.util.Optional;
import java.util.UUID;

public interface UserAccountRepository {
    Optional<UserAccount> findByGithubUserId(long githubUserId);

    default Optional<UserAccount> findByUuid(UUID id) {
        return Optional.empty();
    }
    UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName);
}



