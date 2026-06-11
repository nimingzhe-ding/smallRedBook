package com.xhs.dto;

import lombok.Data;

@Data
public class LoginResponseDTO {
    private String token;
    private Long expiresInSeconds;
    private UserDTO user;
}
