package in.ruhmans.alliancetrading.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** One bucket for every document; the object key's prefix separates the kinds (see {@code FileService}). */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucket) {}
