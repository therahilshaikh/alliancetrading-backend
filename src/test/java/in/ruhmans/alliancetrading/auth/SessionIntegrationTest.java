package in.ruhmans.alliancetrading.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.UUID;

import in.ruhmans.alliancetrading.BaseIntegrationTest;
import in.ruhmans.alliancetrading.auth.domain.Session;
import in.ruhmans.alliancetrading.auth.repository.SessionRepository;
import in.ruhmans.alliancetrading.auth.service.SessionService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SessionIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private SessionService sessionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private SessionRepository sessionRepository;

    @Autowired
    private Clock clock;

    @Test
    void singleActiveSessionEnforced() {
        User user = userRepository.save(new User("9000000001", clock.instant()));

        // Create first session
        SessionService.SessionTokens s1 = sessionService.createUserSession(
                user.getId(), "USER", "device1", "info1");
        assertThat(s1.accessToken()).isNotBlank();

        // Create second session → first should be revoked
        SessionService.SessionTokens s2 = sessionService.createUserSession(
                user.getId(), "USER", "device2", "info2");
        assertThat(s2.sessionId()).isNotEqualTo(s1.sessionId());

        // First session should be revoked
        assertThat(sessionService.isSessionActive(s1.sessionId())).isFalse();
        assertThat(sessionService.isSessionActive(s2.sessionId())).isTrue();
    }

    @Test
    void refreshTokenRotation() {
        User user = userRepository.save(new User("9000000002", clock.instant()));
        SessionService.SessionTokens tokens = sessionService.createUserSession(
                user.getId(), "USER", "dev1", "info");

        // Refresh should work
        SessionService.SessionTokens refreshed = sessionService.refresh(tokens.refreshToken());
        assertThat(refreshed.accessToken()).isNotBlank();
        assertThat(refreshed.refreshToken()).isNotEqualTo(tokens.refreshToken());
    }

    @Test
    void refreshTokenReuseRevokesSession() {
        User user = userRepository.save(new User("9000000003", clock.instant()));
        SessionService.SessionTokens tokens = sessionService.createUserSession(
                user.getId(), "USER", "dev1", "info");

        String originalRefresh = tokens.refreshToken();

        // Use the refresh token (rotates it)
        sessionService.refresh(originalRefresh);

        // Reuse the OLD refresh token → session should be revoked
        assertThatThrownBy(() -> sessionService.refresh(originalRefresh))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("SESSION_REPLACED"));

        // Session should be revoked
        assertThat(sessionService.isSessionActive(tokens.sessionId())).isFalse();
    }

    @Test
    void logoutRevokesSession() {
        User user = userRepository.save(new User("9000000004", clock.instant()));
        SessionService.SessionTokens tokens = sessionService.createUserSession(
                user.getId(), "USER", "dev1", "info");

        assertThat(sessionService.isSessionActive(tokens.sessionId())).isTrue();
        sessionService.logout(tokens.sessionId());
        assertThat(sessionService.isSessionActive(tokens.sessionId())).isFalse();
    }
}
