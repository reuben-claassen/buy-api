package com.buyapi.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AuthRequest {

    public record Register(
            @NotBlank @Email String email,
            @NotBlank @Size(min = 8, message = "Password must be at least 8 characters") String password,
            @NotBlank @Size(max = 100) String fullName,
            String role
    ) {}

    public record Login(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {}
}