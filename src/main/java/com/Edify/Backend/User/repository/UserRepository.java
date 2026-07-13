package com.Edify.Backend.User.repository;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.User.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {

    //Find User using mail
    Optional<User> findByEmail(String email);

    //Find oAuth user
    Optional<User> findByProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);

    // check email exist
    boolean existsByEmail(String email);

    //check proverID already exist
    boolean existsByProviderAndProviderUserId(AuthProvider authProvider, String providerUserId);
}
