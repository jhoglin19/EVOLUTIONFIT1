package com.evolutionfit.modules.auth.dto;

import com.evolutionfit.modules.users.dto.UserResponse;

public record LoginResponse(String token,
                            String tokenType,
                            long expiresInSeconds,
                            UserResponse user) {
}
