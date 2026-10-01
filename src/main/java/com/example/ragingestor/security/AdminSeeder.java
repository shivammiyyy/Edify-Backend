package com.example.ragingestor.security;

import com.example.ragingestor.domain.AppUser;
import com.example.ragingestor.domain.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements ApplicationRunner {

    private final AppUserRepository appUserRepository;
    private final String adminEmail;

    public AdminSeeder(AppUserRepository appUserRepository, @Value("${app.security.admin-email}") String adminEmail) {
        this.appUserRepository = appUserRepository;
        this.adminEmail = adminEmail.toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    public void run(ApplicationArguments args) {
        appUserRepository.findByEmail(adminEmail).ifPresentOrElse(
                existing -> {
                    if (!existing.isSystemAdmin() || existing.getRole() != Role.ADMIN) {
                        existing.setRole(Role.ADMIN);
                        existing.setSystemAdmin(true);
                        appUserRepository.save(existing);
                    }
                },
                () -> {
                    AppUser admin = new AppUser(adminEmail, "System Admin", "seed", Role.ADMIN);
                    admin.setSystemAdmin(true);
                    appUserRepository.save(admin);
                }
        );
    }
}
