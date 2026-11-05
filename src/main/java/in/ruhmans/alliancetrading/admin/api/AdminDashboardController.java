package in.ruhmans.alliancetrading.admin.api;

import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.admin.service.AdminDashboardService;
import in.ruhmans.alliancetrading.admin.service.AdminDashboardService.DashboardSummary;
import in.ruhmans.alliancetrading.common.security.SecurityUtils;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    public ResponseEntity<DashboardSummary> dashboard() {
        return ResponseEntity.ok(dashboardService.summary(SecurityUtils.currentAdminId()));
    }
}
