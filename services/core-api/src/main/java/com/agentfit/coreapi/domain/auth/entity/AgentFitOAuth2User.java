package com.agentfit.coreapi.domain.auth.entity;


import com.agentfit.coreapi.domain.auth.entity.*;
import com.agentfit.coreapi.domain.auth.repository.*;
import com.agentfit.coreapi.domain.auth.service.*;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.user.OAuth2User;

public final class AgentFitOAuth2User implements OAuth2User, Serializable {
    private static final long serialVersionUID = 1L;

    private final UserAccount account;
    private final Map<String, Object> attributes;
    private final Collection<? extends GrantedAuthority> authorities;

    public AgentFitOAuth2User(UserAccount account, Map<String, Object> attributes,
            Collection<? extends GrantedAuthority> authorities) {
        this.account = account;
        this.attributes = Map.copyOf(attributes);
        this.authorities = authorities == null ? Collections.emptyList() : ListCopy.copy(authorities);
    }

    UserAccount account() {
        return account;
    }

    @Override
    public Map<String, Object> getAttributes() {
        return attributes;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getName() {
        if (account.uuid() == null) {
            throw new IllegalStateException("V5 OAuth principal requires a UUID user id");
        }
        return account.uuid().toString();
    }

    private static final class ListCopy {
        private static <T> Collection<T> copy(Collection<? extends T> source) {
            return java.util.List.copyOf(source);
        }
    }
}



