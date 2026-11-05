package in.ruhmans.alliancetrading.user.service;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import in.ruhmans.alliancetrading.kyc.domain.KycProfile;
import in.ruhmans.alliancetrading.kyc.repository.KycProfileRepository;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Resolves user ids to display labels (name from KYC, mobile) in one batched lookup. */
@Service
public class UserDirectory {

    public record UserLabel(UUID userId, String fullName, String mobile) {}

    private final UserRepository userRepository;
    private final KycProfileRepository kycRepository;

    public UserDirectory(UserRepository userRepository, KycProfileRepository kycRepository) {
        this.userRepository = userRepository;
        this.kycRepository = kycRepository;
    }

    @Transactional(readOnly = true)
    public Map<UUID, UserLabel> labels(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> names = new HashMap<>();
        for (KycProfile k : kycRepository.findByUserIdIn(userIds)) {
            names.put(k.getUserId(), k.getFullName());
        }
        Map<UUID, UserLabel> labels = new HashMap<>();
        for (User u : userRepository.findAllById(userIds)) {
            labels.put(u.getId(), new UserLabel(u.getId(), names.get(u.getId()), u.getMobile()));
        }
        return labels;
    }
}
