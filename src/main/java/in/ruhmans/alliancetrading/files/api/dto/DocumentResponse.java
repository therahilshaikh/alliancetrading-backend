package in.ruhmans.alliancetrading.files.api.dto;

import java.time.Instant;
import java.util.UUID;

import in.ruhmans.alliancetrading.files.domain.DocType;

/** {@code originalFilename} is the cleaned upload name (null for files without one). */
public record DocumentResponse(
        UUID id,
        DocType docType,
        String originalFilename,
        String mime,
        long sizeBytes,
        String sha256,
        Instant createdAt) {}
