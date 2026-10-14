package com.agentfit.coreapi.domain.auth.entity;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.io.Serializable;

public record UserAccount(long id, String name, long githubUserId, String githubLogin)
        implements Serializable {
    private static final long serialVersionUID = 1L;
}



