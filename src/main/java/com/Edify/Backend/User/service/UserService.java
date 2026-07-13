package com.Edify.Backend.User.service;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.User.entity.User;

import java.util.Optional;
import java.util.UUID;

public interface UserService {

    User createUser(User user);

    User updateUser(User user);

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    Optional<User> findByProvider(AuthProvider authProvider, String providerUserId);

    boolean existsByEmail(String email);

    void  updateLastLogin(UUID id);
}
