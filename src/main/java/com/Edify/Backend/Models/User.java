package com.Edify.Backend.Models;

import com.Edify.Backend.Domain.AuthProvider;
import com.Edify.Backend.Domain.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import lombok.*;
import org.springframework.data.annotation.Id;

import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Table(name = "users")
@Getter
@Setter
public class User {

    @Id
    private UUID id;

    private AuthProvider authProvider;

    @Column(unique = true)
    private String email;

    private String profilePicture;

    private String name;

    private String adsId;

    private Role role;

}
