package com.eduplay.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The current password is required so a stolen access token alone cannot delete the account. */
public record DeleteAccountRequest(@NotBlank @Size(max = 128) String password) {
}
