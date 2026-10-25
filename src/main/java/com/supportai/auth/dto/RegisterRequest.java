
package com.supportai.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Defines the information a new user must provide during registration.
// Public users cannot choose their own role.
public record RegisterRequest(

        // Name is required and cannot exceed 100 characters.
        @NotBlank(message = "Name is required")
        @Size(
                max = 100,
                message = "Name must not exceed 100 characters"
        )
        String name,

        // Email is required and must have a valid format.
        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(
                max = 255,
                message = "Email must not exceed 255 characters"
        )
        String email,

        // Password is required and must be 8–72 characters long.
        @NotBlank(message = "Password is required")
        @Size(
                min = 8,
                max = 72,
                message = "Password must be between 8 and 72 characters"
        )
        String password

) {
}
