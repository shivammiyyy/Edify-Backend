package com.Edify.Backend.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class AuthStatusResponse {
    private boolean authenticated;
}
