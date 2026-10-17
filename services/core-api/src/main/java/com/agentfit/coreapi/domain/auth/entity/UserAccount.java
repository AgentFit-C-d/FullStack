package com.agentfit.coreapi.domain.auth.entity;

import java.io.Serializable;
import java.util.UUID;

public record UserAccount(long id, String name, long githubUserId, String githubLogin, UUID uuid)
        implements Serializable {
    private static final long serialVersionUID = 1L;

    public UserAccount(long id, String name, long githubUserId, String githubLogin) {
        this(id, name, githubUserId, githubLogin, null);
    }

    public UserAccount(UUID uuid, String name, long githubUserId, String githubLogin) {
        this(0L, name, githubUserId, githubLogin, uuid);
    }
}



