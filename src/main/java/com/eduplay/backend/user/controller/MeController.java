package com.eduplay.backend.user.controller;

import com.eduplay.backend.common.util.ClientIp;
import com.eduplay.backend.security.AuthenticatedUser;
import com.eduplay.backend.user.dto.AccountDeletionResponse;
import com.eduplay.backend.user.dto.ConsentRequest;
import com.eduplay.backend.user.dto.ConsentResponse;
import com.eduplay.backend.user.dto.DataExportResponse;
import com.eduplay.backend.user.dto.DeleteAccountRequest;
import com.eduplay.backend.user.dto.UpdateProfileRequest;
import com.eduplay.backend.user.dto.UserResponse;
import com.eduplay.backend.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Profile")
@RestController
@RequestMapping("/api/v1/me")
@RequiredArgsConstructor
public class MeController {

    private final UserService userService;

    @Operation(operationId = "getProfile", summary = "Current parent profile")
    @GetMapping
    public UserResponse me(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.getProfile(user.id());
    }

    @Operation(operationId = "updateProfile", summary = "Update name and/or phone (partial)")
    @PatchMapping
    public UserResponse update(@AuthenticationPrincipal AuthenticatedUser user,
                               @Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(user.id(), request);
    }

    @Operation(operationId = "deleteAccount", summary = "Delete the account", description = """
            Requires the current password. Signs out all devices and hides the account and child profiles
            immediately; all data is permanently erased after 30 days.""")
    @DeleteMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public AccountDeletionResponse delete(@AuthenticationPrincipal AuthenticatedUser user,
                                          @Valid @RequestBody DeleteAccountRequest request) {
        return userService.deleteAccount(user.id(), request.password());
    }

    @Operation(operationId = "exportData", summary = "Export all personal data held for this account and its children")
    @GetMapping("/export")
    public DataExportResponse export(@AuthenticationPrincipal AuthenticatedUser user) {
        return userService.export(user.id());
    }

    @Operation(operationId = "acceptPolicy", summary = "Accept the current privacy policy version")
    @PostMapping("/consent")
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse consent(@AuthenticationPrincipal AuthenticatedUser user,
                                   @Valid @RequestBody ConsentRequest request, HttpServletRequest http) {
        return userService.recordConsent(user.id(), request.policyVersion(), ClientIp.of(http));
    }
}
