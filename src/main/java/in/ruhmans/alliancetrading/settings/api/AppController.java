package in.ruhmans.alliancetrading.settings.api;

import in.ruhmans.alliancetrading.settings.service.SettingsService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/app")
public class AppController {

    private final SettingsService settingsService;

    public AppController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping("/version")
    public ResponseEntity<AppVersionResponse> version() {
        return ResponseEntity.ok(new AppVersionResponse(settingsService.minAppVersion()));
    }

    public record AppVersionResponse(String minAppVersion) {}
}
