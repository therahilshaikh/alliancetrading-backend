package in.ruhmans.alliancetrading.kyc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.crypto.CryptoProperties;
import in.ruhmans.alliancetrading.common.crypto.FieldEncryptor;
import in.ruhmans.alliancetrading.common.crypto.FieldHasher;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.kyc.api.dto.KycSubmitRequest;
import in.ruhmans.alliancetrading.kyc.domain.KycProfile;
import in.ruhmans.alliancetrading.kyc.repository.KycProfileRepository;
import in.ruhmans.alliancetrading.kyc.service.KycService;
import in.ruhmans.alliancetrading.notification.service.OutboxService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class KycServiceTest {

    /** 12 digits with a valid Verhoeff check digit (see AadhaarValidatorTest). */
    static final String VALID_AADHAAR = AadhaarValidatorTest.withCheckDigit("23412341234");

    private KycProfileRepository kycRepository;
    private UserRepository userRepository;
    private OutboxService outboxService;
    private FieldEncryptor fieldEncryptor;
    private KycService kycService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        kycRepository = Mockito.mock(KycProfileRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        outboxService = Mockito.mock(OutboxService.class);
        clock = Clock.fixed(Instant.parse("2024-06-24T05:00:00Z"), ZoneId.of("Asia/Kolkata"));

        byte[] key = new byte[32];
        var cryptoProps = new CryptoProperties(Base64.getEncoder().encodeToString(key), "k1",
                Base64.getEncoder().encodeToString("pepper".getBytes()));
        fieldEncryptor = new FieldEncryptor(cryptoProps);

        kycService = new KycService(kycRepository, userRepository, fieldEncryptor,
                new FieldHasher(cryptoProps), Mockito.mock(AuditService.class), outboxService, clock);
    }

    private KycSubmitRequest validRequest() {
        return new KycSubmitRequest("Test User", LocalDate.of(1990, 1, 1), "ABCDE1234F", VALID_AADHAAR);
    }

    private User registeredUser() {
        User user = new User("9876543210", clock.instant());
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(kycRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(kycRepository.findByPanHash(any())).thenReturn(Optional.empty());
        when(kycRepository.findByAadhaarHash(any())).thenReturn(Optional.empty());
        when(kycRepository.save(any(KycProfile.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        return user;
    }

    @Test
    void submitStoresFullNumbersEncryptedAndMasksForDisplay() {
        User user = registeredUser();

        KycProfile profile = kycService.submit(user.getId(), validRequest());

        assertThat(user.getStatus()).isEqualTo(UserStatus.KYC_SUBMITTED);
        assertThat(profile.getPanLast4()).isEqualTo("234F");
        assertThat(profile.getAadhaarLast4()).isEqualTo(VALID_AADHAAR.substring(8));
        assertThat(profile.getAadhaarEnc()).isNotEqualTo(VALID_AADHAAR);
        assertThat(fieldEncryptor.decrypt(profile.getAadhaarEnc())).isEqualTo(VALID_AADHAAR);
        assertThat(fieldEncryptor.decrypt(profile.getPanEnc())).isEqualTo("ABCDE1234F");
        assertThat(profile.getPanCardDocumentId()).isNull();
    }

    @Test
    void submitRejectsInvalidAadhaarChecksum() {
        User user = registeredUser();
        String badChecksum = VALID_AADHAAR.substring(0, 11) + ((VALID_AADHAAR.charAt(11) - '0' + 1) % 10);
        var req = new KycSubmitRequest("Test User", LocalDate.of(1990, 1, 1), "ABCDE1234F", badChecksum);

        assertThatThrownBy(() -> kycService.submit(user.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("Invalid Aadhaar");
    }

    @Test
    void submitRejectsUnder18() {
        User user = registeredUser();
        var req = new KycSubmitRequest("Teen", LocalDate.of(2010, 1, 1), "ABCDE1234F", VALID_AADHAAR);

        assertThatThrownBy(() -> kycService.submit(user.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("18");
    }

    @Test
    void submitFromApprovedRejected() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.APPROVED);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> kycService.submit(user.getId(), validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void panOwnedByAnotherUserRejected() {
        User user = registeredUser();
        when(kycRepository.findByPanHash(any()))
                .thenReturn(Optional.of(new KycProfile(UUID.randomUUID(), clock.instant())));

        assertThatThrownBy(() -> kycService.submit(user.getId(), validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("DUPLICATE_PAN"));
    }

    @Test
    void aadhaarOwnedByAnotherUserRejected() {
        User user = registeredUser();
        when(kycRepository.findByAadhaarHash(any()))
                .thenReturn(Optional.of(new KycProfile(UUID.randomUUID(), clock.instant())));

        assertThatThrownBy(() -> kycService.submit(user.getId(), validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("DUPLICATE_AADHAAR"));
    }

    @Test
    void resubmissionWithOwnNumbersAllowed() {
        User user = registeredUser();
        user.setStatus(UserStatus.REJECTED);
        KycProfile own = new KycProfile(user.getId(), clock.instant());
        when(kycRepository.findByUserId(user.getId())).thenReturn(Optional.of(own));
        when(kycRepository.findByPanHash(any())).thenReturn(Optional.of(own));
        when(kycRepository.findByAadhaarHash(any())).thenReturn(Optional.of(own));

        KycProfile profile = kycService.submit(user.getId(), validRequest());

        assertThat(profile).isSameAs(own);
        assertThat(user.getStatus()).isEqualTo(UserStatus.KYC_SUBMITTED);
    }

    @Test
    void approveTransitionsUserToApprovedAndNotifies() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.KYC_SUBMITTED);
        UUID userId = user.getId();

        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(new KycProfile(userId, clock.instant())));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        kycService.approve(userId, UUID.randomUUID());

        assertThat(user.getStatus()).isEqualTo(UserStatus.APPROVED);
        verify(outboxService).notifyUser(eq("KYC_APPROVED"), eq(userId), any());
    }

    @Test
    void rejectRequiresReason() {
        User user = new User("9876543210", clock.instant());
        user.setStatus(UserStatus.KYC_SUBMITTED);
        UUID userId = user.getId();

        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(new KycProfile(userId, clock.instant())));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> kycService.reject(userId, UUID.randomUUID(), null))
                .isInstanceOf(ApiException.class);
    }

    @Test
    void approveFromWrongStateFails() {
        User user = new User("9876543210", clock.instant());
        UUID userId = user.getId();

        when(kycRepository.findByUserId(userId)).thenReturn(Optional.of(new KycProfile(userId, clock.instant())));
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> kycService.approve(userId, UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void revealDecryptsFullNumberForAdmin() {
        User user = registeredUser();
        KycProfile profile = kycService.submit(user.getId(), validRequest());
        when(kycRepository.findByUserId(user.getId())).thenReturn(Optional.of(profile));

        assertThat(kycService.revealIdentityNumber(user.getId(), KycService.IdentityField.AADHAAR, UUID.randomUUID()))
                .isEqualTo(VALID_AADHAAR);
    }
}
