package com.eventprocessor.reliable_event_processor.controller;

import com.eventprocessor.reliable_event_processor.dto.CreateUserRequest;
import com.eventprocessor.reliable_event_processor.dto.PageResponse;
import com.eventprocessor.reliable_event_processor.dto.UpdateRoleRequest;
import com.eventprocessor.reliable_event_processor.dto.UpdateStatusRequest;
import com.eventprocessor.reliable_event_processor.dto.UserResponse;
import com.eventprocessor.reliable_event_processor.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<UserResponse> createUser(@Valid @RequestBody CreateUserRequest request,
                                                   Authentication authentication) {
        String principal = authentication != null ? authentication.getName() : "ADMIN";
        UserResponse response = userService.createUser(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<PageResponse<UserResponse>> listUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(userService.listUsers(page, size));
    }

    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponse> updateUserRole(@PathVariable Long id,
                                                       @Valid @RequestBody UpdateRoleRequest request,
                                                       Authentication authentication) {
        String principal = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(userService.updateUserRole(id, request.role(), principal));
    }

    @PatchMapping("/{id}/status")
    public ResponseEntity<UserResponse> updateUserStatus(@PathVariable Long id,
                                                         @Valid @RequestBody UpdateStatusRequest request,
                                                         Authentication authentication) {
        String principal = authentication != null ? authentication.getName() : "ADMIN";
        return ResponseEntity.ok(userService.updateUserStatus(id, request.status(), principal));
    }
}

