package com.Edify.Backend.User.service.implementation;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.User.entity.User;
import com.Edify.Backend.User.repository.UserRepository;
import com.Edify.Backend.User.service.UserService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final UserRepository userRepository;
    @Override
    public User createUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public User updateUser(User user) {
        return userRepository.save(user);
    }

    @Override
    public Optional<User> findById(UUID id) {
        return userRepository.findById(id);
    }

    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }

    @Override
    public Optional<User> findByProvider(AuthProvider authProvider, String providerUserId) {
        return userRepository.findByProviderAndProviderUserId(authProvider, providerUserId);
    }

    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }

    @Override
    public void updateLastLogin(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(
                        () -> new EntityNotFoundException("User with id " + id + " not found")
                );
        user.setLastLogin(LocalDateTime.now());
        userRepository.save(user);
    }
}
