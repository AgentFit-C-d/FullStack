package com.agentfit.coreapi.domain.auth.repository;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.util.Optional;

public interface UserAccountRepository {
    Optional<UserAccount> findByGithubUserId(long githubUserId);

    Optional<UserAccount> findById(long id);

    UserAccount createFromGithub(long githubUserId, String githubLogin, String displayName);
}



