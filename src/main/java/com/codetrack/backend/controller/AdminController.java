package com.codetrack.backend.controller;

import com.codetrack.backend.dto.AdminSummary;
import com.codetrack.backend.entity.Admin;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
@Tag(name = "Admin", description = "Authenticated admin profile operations")
public class AdminController {

    @GetMapping("/me")
    public AdminSummary me(@AuthenticationPrincipal Admin admin) {
        return new AdminSummary(admin.getId(), admin.getEmail(), admin.getFullName(), admin.getCollegeName());
    }
}
