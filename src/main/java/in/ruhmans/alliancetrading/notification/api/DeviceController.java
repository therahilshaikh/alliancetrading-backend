package in.ruhmans.alliancetrading.notification.api;

import java.time.Clock;

import in.ruhmans.alliancetrading.common.security.SecurityUtils;
import in.ruhmans.alliancetrading.notification.domain.UserDevice;
import in.ruhmans.alliancetrading.notification.repository.UserDeviceRepository;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/devices")
public class DeviceController {

    private final UserDeviceRepository userDeviceRepository;
    private final Clock clock;

    public DeviceController(UserDeviceRepository userDeviceRepository, Clock clock) {
        this.userDeviceRepository = userDeviceRepository;
        this.clock = clock;
    }

    public record PushTokenRequest(@NotBlank @Size(max = 500) String pushToken) {}

    @PutMapping("/push-token")
    public ResponseEntity<Void> registerPushToken(@RequestBody PushTokenRequest request) {
        var userId = SecurityUtils.currentUserId();
        var device = userDeviceRepository.findById(userId)
                .orElse(new UserDevice(userId, request.pushToken(), clock.instant()));
        device.updateToken(request.pushToken(), clock.instant());
        userDeviceRepository.save(device);
        return ResponseEntity.ok().build();
    }
}
