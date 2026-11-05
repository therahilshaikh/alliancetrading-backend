package in.ruhmans.alliancetrading.files.service;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import javax.imageio.ImageIO;

import in.ruhmans.alliancetrading.audit.service.AuditService;
import in.ruhmans.alliancetrading.common.config.StorageProperties;
import in.ruhmans.alliancetrading.common.crypto.EnvelopeEncryptionService;
import in.ruhmans.alliancetrading.common.error.ApiException;
import in.ruhmans.alliancetrading.common.error.ErrorCode;
import in.ruhmans.alliancetrading.files.domain.DocType;
import in.ruhmans.alliancetrading.files.domain.Document;
import in.ruhmans.alliancetrading.files.repository.DocumentRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);
    private static final long MAX_BYTES = 5L * 1024 * 1024;

    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};
    private static final byte[] PDF_MAGIC = {0x25, 0x50, 0x44, 0x46};

    private static final Map<String, String> MIME_EXT = Map.of(
            "image/jpeg", "jpg",
            "image/png", "png",
            "application/pdf", "pdf");

    private final S3Client s3;
    private final StorageProperties storageProps;
    private final EnvelopeEncryptionService encryptionService;
    private final DocumentRepository documentRepository;
    private final AuditService auditService;
    private final Clock clock;

    public FileService(S3Client s3, StorageProperties storageProps,
                       EnvelopeEncryptionService encryptionService,
                       DocumentRepository documentRepository,
                       AuditService auditService, Clock clock) {
        this.s3 = s3;
        this.storageProps = storageProps;
        this.encryptionService = encryptionService;
        this.documentRepository = documentRepository;
        this.auditService = auditService;
        this.clock = clock;
    }

    /** {@code filename} is the name to download as: the cleaned original name, or {@code <doc_type>_<id>.<ext>}. */
    public record ContentResult(byte[] bytes, String mime, String sha256, String filename) {}

    @Transactional
    public Document upload(MultipartFile file, DocType docType,
                           UUID ownerUserId, String uploadedByType, UUID uploadedById) {
        return upload(readBytes(file), file.getOriginalFilename(), docType, ownerUserId, uploadedByType, uploadedById);
    }

    /** Same as the multipart upload, for content produced on the server (e.g. demo seed files). */
    @Transactional
    public Document upload(byte[] raw, String originalFilename, DocType docType,
                           UUID ownerUserId, String uploadedByType, UUID uploadedById) {
        if (raw.length > MAX_BYTES) {
            throw new ApiException(ErrorCode.FILE_TOO_LARGE, "File exceeds 5 MB limit");
        }

        String mime = detectMime(raw);
        byte[] processed = isImage(mime) ? stripExif(raw, mime) : raw;

        String sha256 = sha256Hex(processed);
        var enc = encryptionService.encrypt(processed);

        String bucket = storageProps.bucket();
        UUID documentId = UUID.randomUUID();
        String objectKey = objectKey(docType, ownerUserId, uploadedById, documentId, MIME_EXT.get(mime));

        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(objectKey)
                        .contentType(mime)
                        .contentLength((long) enc.ciphertext().length)
                        .build(),
                RequestBody.fromBytes(enc.ciphertext()));

        Document doc = new Document(documentId, ownerUserId, docType, bucket, objectKey, mime,
                processed.length, sha256, enc.wrappedDataKey(), enc.keyId(),
                uploadedByType, uploadedById, clock.instant(),
                cleanFilename(originalFilename, MIME_EXT.get(mime)));
        return documentRepository.save(doc);
    }

    private static final int MAX_FILENAME_BASE = 200;

    /**
     * The upload name made safe to store and send back in Content-Disposition: directory parts, control
     * characters and characters invalid in filenames are removed, length is capped, and the extension is
     * replaced with the one for the detected type (so a JPEG renamed .png downloads as .jpg). Null if
     * nothing usable is left.
     */
    static String cleanFilename(String original, String ext) {
        if (original == null) {
            return null;
        }
        String name = original.substring(Math.max(original.lastIndexOf('/'), original.lastIndexOf('\\')) + 1);
        name = name.replaceAll("[\\p{Cntrl}\"*:<>?|]", "").replaceAll("\\s+", " ").strip();
        int dot = name.lastIndexOf('.');
        String base = (dot > 0 ? name.substring(0, dot) : name).replaceAll("^[.\\s]+|[.\\s]+$", "");
        if (base.isEmpty()) {
            return null;
        }
        if (base.length() > MAX_FILENAME_BASE) {
            base = base.substring(0, MAX_FILENAME_BASE).strip();
        }
        return base + "." + ext;
    }

    /** Download name: the cleaned original, or {@code <doc_type>_<id>.<ext>} for files stored without one. */
    static String downloadFilename(Document doc) {
        if (doc.getOriginalFilename() != null) {
            return doc.getOriginalFilename();
        }
        String ext = MIME_EXT.getOrDefault(doc.getMime(), "bin");
        return doc.getDocType().name().toLowerCase(java.util.Locale.ROOT) + "_" + doc.getId() + "." + ext;
    }

    /**
     * {@code <folder>/<ownerUserId>/<doc_type>_<documentId>.<ext>} for documents about a member (KYC, payment
     * proof, and admin-uploaded payout proof), e.g. {@code kyc/<userId>/pan_card_<id>.jpg};
     * {@code admin/<adminId>/<doc_type>_<documentId>.<ext>} for admin-only files with no member owner
     * (P&L evidence, payment QR). The document ID keeps every key unique, so re-uploads never overwrite.
     */
    static String objectKey(DocType docType, UUID ownerUserId, UUID uploadedById, UUID documentId, String ext) {
        String name = docType.name().toLowerCase(java.util.Locale.ROOT) + "_" + documentId + "." + ext;
        return ownerUserId != null
                ? docType.folder() + "/" + ownerUserId + "/" + name
                : "admin/" + uploadedById + "/" + name;
    }

    @Transactional(readOnly = true)
    public ContentResult getContentForUser(UUID documentId, UUID userId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
        if (doc.getOwnerUserId() == null || !doc.getOwnerUserId().equals(userId)) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not your document");
        }
        return fetchAndDecrypt(doc);
    }

    @Transactional
    public ContentResult getContentForAdmin(UUID documentId, UUID adminId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
        auditService.recordAdmin(adminId, "DOCUMENT_VIEWED", "document", documentId.toString(),
                null, "{\"docType\":\"" + doc.getDocType() + "\"}");
        return fetchAndDecrypt(doc);
    }

    /** For admin-uploaded shared documents (e.g. payment QR) viewable by approved users. */
    @Transactional(readOnly = true)
    public ContentResult getSharedContent(UUID documentId) {
        Document doc = documentRepository.findById(documentId)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "Document not found"));
        if (doc.getOwnerUserId() != null) {
            throw new ApiException(ErrorCode.FORBIDDEN, "Not a shared document");
        }
        return fetchAndDecrypt(doc);
    }

    private ContentResult fetchAndDecrypt(Document doc) {
        byte[] ciphertext;
        try {
            ciphertext = s3.getObjectAsBytes(GetObjectRequest.builder()
                            .bucket(doc.getBucket())
                            .key(doc.getObjectKey())
                            .build())
                    .asByteArray();
        } catch (NoSuchKeyException | NoSuchBucketException e) {
            throw missingFile(doc);
        } catch (software.amazon.awssdk.services.s3.model.S3Exception e) {
            // Some S3-compatible stores (R2) report a missing object as a plain 404
            if (e.statusCode() == 404) {
                throw missingFile(doc);
            }
            throw e;
        }
        byte[] plaintext = encryptionService.decrypt(ciphertext, doc.getWrappedKey());
        return new ContentResult(plaintext, doc.getMime(), doc.getSha256(), downloadFilename(doc));
    }

    /** The row exists but the stored file does not (deleted from storage, or a row created without a file). */
    private static ApiException missingFile(Document doc) {
        log.warn("Stored file missing for document {} ({}/{})", doc.getId(), doc.getBucket(), doc.getObjectKey());
        return new ApiException(ErrorCode.NOT_FOUND, "The file for this document is no longer in storage");
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Could not read uploaded file");
        }
    }

    private String detectMime(byte[] data) {
        if (startsWith(data, JPEG_MAGIC)) return "image/jpeg";
        if (startsWith(data, PNG_MAGIC)) return "image/png";
        if (startsWith(data, PDF_MAGIC)) return "application/pdf";
        throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                "Only JPEG, PNG and PDF files are allowed");
    }

    private boolean startsWith(byte[] data, byte[] magic) {
        if (data.length < magic.length) return false;
        for (int i = 0; i < magic.length; i++) {
            if (data[i] != magic[i]) return false;
        }
        return true;
    }

    private boolean isImage(String mime) {
        return mime.equals("image/jpeg") || mime.equals("image/png");
    }

    /** Re-encode via ImageIO, which drops EXIF and other metadata. */
    private byte[] stripExif(byte[] data, String mime) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Corrupt image file");
            }
            String format = mime.equals("image/png") ? "png" : "jpg";
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(image, format, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new ApiException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "Could not process image");
        }
    }

    private String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
