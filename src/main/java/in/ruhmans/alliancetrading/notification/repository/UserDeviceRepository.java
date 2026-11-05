package in.ruhmans.alliancetrading.notification.repository;

import java.util.UUID;

import in.ruhmans.alliancetrading.notification.domain.UserDevice;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
}
