package in.ruhmans.alliancetrading.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.bank.api.dto.BankAccountRequest;
import in.ruhmans.alliancetrading.bank.domain.BankAccount;
import in.ruhmans.alliancetrading.bank.domain.BankAccountStatus;
import in.ruhmans.alliancetrading.bank.domain.BankAccountType;
import in.ruhmans.alliancetrading.bank.repository.BankAccountRepository;
import in.ruhmans.alliancetrading.bank.service.BankAccountService;
import in.ruhmans.alliancetrading.common.crypto.CryptoProperties;
import in.ruhmans.alliancetrading.common.crypto.FieldEncryptor;
import in.ruhmans.alliancetrading.common.crypto.FieldHasher;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.settings.service.SettingsService;
import in.ruhmans.alliancetrading.user.domain.User;
import in.ruhmans.alliancetrading.user.domain.UserStatus;
import in.ruhmans.alliancetrading.user.repository.UserRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class BankAccountServiceTest {

    private BankAccountRepository bankAccountRepository;
    private UserRepository userRepository;
    private DocumentRepository documentRepository;
    private BankAccountService bankAccountService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        bankAccountRepository = Mockito.mock(BankAccountRepository.class);
        userRepository = Mockito.mock(UserRepository.class);
        documentRepository = Mockito.mock(DocumentRepository.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        byte[] key = new byte[32];
        var cryptoProps = new CryptoProperties(Base64.getEncoder().encodeToString(key), "k1",
                Base64.getEncoder().encodeToString("pepper".getBytes()));

        var settingsService = Mockito.mock(SettingsService.class);
        when(settingsService.bankChangeCooloffHours()).thenReturn(48L);

        bankAccountService = new BankAccountService(bankAccountRepository, userRepository,
                documentRepository, new FieldEncryptor(cryptoProps), new FieldHasher(cryptoProps),
                Mockito.mock(AuditService.class), settingsService, clock);
    }

    private BankAccountRequest validRequest() {
        return new BankAccountRequest("Test User", "123456789012", "SBIN0123456",
                BankAccountType.SAVINGS, "user@upi", null);
    }

    private User userWithStatus(UserStatus status) {
        User user = new User("9876543210", clock.instant());
        user.setStatus(status);
        when(userRepository.findByIdForUpdate(user.getId())).thenReturn(Optional.of(user));
        when(bankAccountRepository.findByUserIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));
        return user;
    }

    @Test
    void suspendedUserCannotSubmit() {
        User user = userWithStatus(UserStatus.SUSPENDED);

        assertThatThrownBy(() -> bankAccountService.submit(user.getId(), validRequest()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

    @Test
    void firstAccountDuringOnboardingHasNoCooloffOrProof() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);

        BankAccount account = bankAccountService.submit(user.getId(), validRequest());

        assertThat(account.getStatus()).isEqualTo(BankAccountStatus.PENDING_VERIFICATION);
        assertThat(account.getCooloffUntil()).isNull();
        assertThat(account.getProofDocumentId()).isNull();
        assertThat(account.getAccountType()).isEqualTo(BankAccountType.SAVINGS);
        assertThat(account.getAccountNumberLast4()).isEqualTo("9012");
    }

    @Test
    void changingVerifiedAccountStartsCooloff() {
        User user = userWithStatus(UserStatus.APPROVED);
        BankAccount verified = new BankAccount(user.getId(), "Old", "enc", "hash", "1111",
                "SBIN0123456", BankAccountType.SAVINGS, null, null, clock.instant(), clock.instant());
        verified.markVerified(UUID.randomUUID(), clock.instant());
        when(bankAccountRepository.findByUserIdAndStatus(user.getId(), BankAccountStatus.VERIFIED))
                .thenReturn(Optional.of(verified));

        BankAccount account = bankAccountService.submit(user.getId(), validRequest());

        assertThat(account.getCooloffUntil()).isAfter(clock.instant());
    }

    @Test
    void newSubmissionReplacesPendingOne() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        BankAccount pending = new BankAccount(user.getId(), "Typo", "enc", "hash", "1111",
                "SBIN0123456", BankAccountType.SAVINGS, null, null, null, clock.instant());
        when(bankAccountRepository.findByUserIdAndStatus(user.getId(), BankAccountStatus.PENDING_VERIFICATION))
                .thenReturn(Optional.of(pending));

        bankAccountService.submit(user.getId(), validRequest());

        assertThat(pending.getStatus()).isEqualTo(BankAccountStatus.REPLACED);
    }

    @Test
    void proofDocumentStillValidatedWhenProvided() {
        User user = userWithStatus(UserStatus.KYC_SUBMITTED);
        UUID otherUser = UUID.randomUUID();
        Document proof = new Document(otherUser, DocType.BANK_PROOF, "test-docs", "k", "image/jpeg",
                10, "a".repeat(64), "w", "k1", "USER", otherUser, clock.instant());
        UUID proofId = UUID.randomUUID();
        when(documentRepository.findById(proofId)).thenReturn(Optional.of(proof));
        var req = new BankAccountRequest("Test User", "123456789012", "SBIN0123456",
                BankAccountType.SAVINGS, "user@upi", proofId);

        assertThatThrownBy(() -> bankAccountService.submit(user.getId(), req))
                .isInstanceOf(ApiException.class)
                .hasMessageContaining("does not belong");
    }

    @Test
    void verifyReplacesOldVerified() {
        UUID userId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        Instant now = clock.instant();

        BankAccount oldAccount = new BankAccount(userId, "Old", "enc1", "hash1", "1111",
                "SBIN0123456", BankAccountType.SAVINGS, null, UUID.randomUUID(), now, now);
        oldAccount.markVerified(UUID.randomUUID(), now);

        BankAccount newAccount = new BankAccount(userId, "New", "enc2", "hash2", "2222",
                "SBIN0123456", BankAccountType.SAVINGS, null, UUID.randomUUID(), now, now);

        when(bankAccountRepository.findById(newAccount.getId())).thenReturn(Optional.of(newAccount));
        when(bankAccountRepository.findByUserIdAndStatus(userId, BankAccountStatus.VERIFIED))
                .thenReturn(Optional.of(oldAccount));
        when(bankAccountRepository.save(any(BankAccount.class))).thenAnswer(inv -> inv.getArgument(0));

        bankAccountService.verify(newAccount.getId(), adminId);

        assertThat(oldAccount.getStatus()).isEqualTo(BankAccountStatus.REPLACED);
        assertThat(newAccount.getStatus()).isEqualTo(BankAccountStatus.VERIFIED);
    }

    @Test
    void verifyFailsWhenNotPending() {
        BankAccount account = new BankAccount(UUID.randomUUID(), "Test", "enc", "hash", "1234",
                "SBIN0123456", BankAccountType.SAVINGS, null, UUID.randomUUID(), clock.instant(), clock.instant());
        account.markVerified(UUID.randomUUID(), clock.instant());

        when(bankAccountRepository.findById(account.getId())).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> bankAccountService.verify(account.getId(), UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name())
                        .isEqualTo("ILLEGAL_STATE_TRANSITION"));
    }

}
