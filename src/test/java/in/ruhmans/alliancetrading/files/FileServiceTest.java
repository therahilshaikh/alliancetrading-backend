package in.ruhmans.alliancetrading.files;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

import javax.imageio.ImageIO;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.config.StorageProperties;
import in.ruhmans.alliancetrading.common.crypto.CryptoProperties;
import in.ruhmans.alliancetrading.common.crypto.EnvelopeEncryptionService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;
import in.ruhmans.alliancetrading.files.service.FileService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

class FileServiceTest {

    private S3Client s3;
    private DocumentRepository documentRepository;
    private AuditService auditService;
    private FileService fileService;
    private Clock clock;

    @BeforeEach
    void setUp() {
        s3 = Mockito.mock(S3Client.class);
        documentRepository = Mockito.mock(DocumentRepository.class);
        auditService = Mockito.mock(AuditService.class);
        clock = Clock.fixed(Instant.now(), ZoneId.of("Asia/Kolkata"));

        byte[] key = new byte[32];
        for (int i = 0; i < 32; i++) key[i] = (byte) i;
        var cryptoProps = new CryptoProperties(Base64.getEncoder().encodeToString(key), "k1",
                Base64.getEncoder().encodeToString("pepper".getBytes()));
        var enc = new EnvelopeEncryptionService(cryptoProps);
        var storageProps = new StorageProperties("http://localhost:9000", "us-east-1",
                "ak", "sk", "test-docs");

        fileService = new FileService(s3, storageProps, enc, documentRepository, auditService, clock);
    }

    @Test
    void rejectsOversizedFile() {
        byte[] big = new byte[6 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile("file", "big.jpg", "image/jpeg", big);
        assertThatThrownBy(() -> fileService.upload(file, DocType.PAN_CARD, UUID.randomUUID(), "USER", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    void rejectsUnknownFileType() {
        byte[] exe = "MZ fake executable".getBytes();
        MockMultipartFile file = new MockMultipartFile("file", "evil.exe", "application/octet-stream", exe);
        assertThatThrownBy(() -> fileService.upload(file, DocType.PAN_CARD, UUID.randomUUID(), "USER", UUID.randomUUID()))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void acceptsJpegAndStripsExif() throws Exception {
        BufferedImage img = new BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);

        MockMultipartFile file = new MockMultipartFile("file", "pan.jpg", "image/jpeg", baos.toByteArray());
        UUID owner = UUID.randomUUID();
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document doc = fileService.upload(file, DocType.PAN_CARD, owner, "USER", owner);

        assertThat(doc.getMime()).isEqualTo("image/jpeg");
        assertThat(doc.getBucket()).isEqualTo("test-docs");
        assertThat(doc.getObjectKey()).isEqualTo("kyc/" + owner + "/pan_card_" + doc.getId() + ".jpg");
        assertThat(doc.getSha256()).hasSize(64);
        // Re-encoded image has no EXIF metadata
        assertThat(doc.getSizeBytes()).isLessThanOrEqualTo(baos.toByteArray().length);
        Mockito.verify(s3).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    void everythingGoesToOneBucketUnderATypeAndOwnerPrefix() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "png", baos);
        MockMultipartFile file = new MockMultipartFile("file", "proof.png", "image/png", baos.toByteArray());
        UUID member = UUID.randomUUID();
        UUID admin = UUID.randomUUID();
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        Document proof = fileService.upload(file, DocType.PAYMENT_PROOF, member, "USER", member);
        Document payout = fileService.upload(file, DocType.PAYOUT_PROOF, member, "ADMIN", admin);
        Document qr = fileService.upload(file, DocType.PAYMENT_QR, null, "ADMIN", admin);

        assertThat(List.of(proof, payout, qr)).extracting(Document::getBucket).containsOnly("test-docs");
        assertThat(proof.getObjectKey()).isEqualTo("payment-proof/" + member + "/payment_proof_" + proof.getId() + ".png");
        // Admin-uploaded, but about a member: kept with that member's files
        assertThat(payout.getObjectKey()).isEqualTo("payout-proof/" + member + "/payout_proof_" + payout.getId() + ".png");
        // Admin-only file with no member owner
        assertThat(qr.getObjectKey()).isEqualTo("admin/" + admin + "/payment_qr_" + qr.getId() + ".png");

        var captor = ArgumentCaptor.forClass(PutObjectRequest.class);
        Mockito.verify(s3, Mockito.times(3)).putObject(captor.capture(), any(RequestBody.class));
        assertThat(captor.getAllValues()).extracting(PutObjectRequest::key)
                .containsExactly(proof.getObjectKey(), payout.getObjectKey(), qr.getObjectKey());
    }

    @Test
    void originalFilenameIsCleanedAndGetsTheDetectedExtension() throws Exception {
        BufferedImage img = new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(img, "jpg", baos);
        UUID owner = UUID.randomUUID();
        when(documentRepository.save(any(Document.class))).thenAnswer(inv -> inv.getArgument(0));

        // Browser-style full path, quotes and a misleading extension; the content is a JPEG
        Document doc = fileService.upload(new MockMultipartFile("file", "C:\\fakepath\\My PAN  \"card\".png",
                "image/png", baos.toByteArray()), DocType.PAN_CARD, owner, "USER", owner);
        assertThat(doc.getOriginalFilename()).isEqualTo("My PAN card.jpg");
        // The original name never reaches the storage key
        assertThat(doc.getObjectKey()).doesNotContain("PAN card");

        Document unnamed = fileService.upload(new MockMultipartFile("file", "..", "image/jpeg", baos.toByteArray()),
                DocType.PAN_CARD, owner, "USER", owner);
        assertThat(unnamed.getOriginalFilename()).isNull();

        Document unicode = fileService.upload(new MockMultipartFile("file", "पैन कार्ड.jpeg", "image/jpeg",
                baos.toByteArray()), DocType.PAN_CARD, owner, "USER", owner);
        assertThat(unicode.getOriginalFilename()).isEqualTo("पैन कार्ड.jpg");
    }

    @Test
    void downloadUsesTheOriginalNameOrAFallback() {
        UUID owner = UUID.randomUUID();
        var enc = new EnvelopeEncryptionService(new CryptoProperties(
                Base64.getEncoder().encodeToString(new byte[32]), "k1", Base64.getEncoder().encodeToString("p".getBytes())));
        var sealed = enc.encrypt("x".getBytes());
        fileService = new FileService(s3, new StorageProperties("e", "r", "a", "s", "test-docs"), enc,
                documentRepository, auditService, clock);
        Document named = new Document(UUID.randomUUID(), owner, DocType.PAYMENT_PROOF, "test-docs", "k1", "image/png",
                1, "a".repeat(64), sealed.wrappedDataKey(), "k1", "USER", owner, clock.instant(), "receipt.png");
        Document unnamed = new Document(owner, DocType.PAYMENT_PROOF, "test-docs", "k2", "image/png",
                1, "a".repeat(64), sealed.wrappedDataKey(), "k1", "USER", owner, clock.instant());
        when(documentRepository.findById(named.getId())).thenReturn(java.util.Optional.of(named));
        when(documentRepository.findById(unnamed.getId())).thenReturn(java.util.Optional.of(unnamed));
        when(s3.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenReturn(software.amazon.awssdk.core.ResponseBytes.fromByteArray(
                        software.amazon.awssdk.services.s3.model.GetObjectResponse.builder().build(), sealed.ciphertext()));

        assertThat(fileService.getContentForUser(named.getId(), owner).filename()).isEqualTo("receipt.png");
        assertThat(fileService.getContentForUser(unnamed.getId(), owner).filename())
                .isEqualTo("payment_proof_" + unnamed.getId() + ".png");
    }

    @Test
    void missingStoredFileIsNotFoundRatherThanAServerError() {
        UUID owner = UUID.randomUUID();
        Document doc = new Document(owner, DocType.PAYMENT_PROOF, "test-docs", "demo/x.png", "image/png",
                1, "a".repeat(64), "w", "k1", "USER", owner, clock.instant());
        when(documentRepository.findById(doc.getId())).thenReturn(java.util.Optional.of(doc));
        when(s3.getObjectAsBytes(any(software.amazon.awssdk.services.s3.model.GetObjectRequest.class)))
                .thenThrow(software.amazon.awssdk.services.s3.model.NoSuchKeyException.builder().message("missing").build())
                .thenThrow(software.amazon.awssdk.services.s3.model.S3Exception.builder().statusCode(404).message("nf").build());

        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> fileService.getContentForUser(doc.getId(), owner))
                    .isInstanceOf(ApiException.class)
                    .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("NOT_FOUND"));
        }
    }

    @Test
    void userCannotViewOthersDocument() {
        UUID docId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID other = UUID.randomUUID();

        Document doc = new Document(owner, DocType.PAN_CARD, "test-docs", "key", "image/jpeg",
                100, "a".repeat(64), "wrapped", "k1", "USER", owner, clock.instant());
        when(documentRepository.findById(docId)).thenReturn(java.util.Optional.of(doc));

        assertThatThrownBy(() -> fileService.getContentForUser(docId, other))
                .isInstanceOf(ApiException.class)
                .satisfies(e -> assertThat(((ApiException) e).code().name()).isEqualTo("FORBIDDEN"));
    }
}
