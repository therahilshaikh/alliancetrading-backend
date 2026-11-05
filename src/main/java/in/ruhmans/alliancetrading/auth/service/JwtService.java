package in.ruhmans.alliancetrading.auth.service;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.JWSSigner;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;

import in.ruhmans.alliancetrading.common.config.SecurityProperties;

import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private final JWSSigner signer;
    private final JWSVerifier verifier;
    private final SecurityProperties.Jwt config;

    public JwtService(SecurityProperties securityProperties) {
        this.config = securityProperties.jwt();
        byte[] keyBytes = Base64.getDecoder().decode(config.secret());
        try {
            this.signer = new MACSigner(keyBytes);
            this.verifier = new MACVerifier(keyBytes);
        } catch (JOSEException e) {
            throw new IllegalStateException("Invalid JWT secret", e);
        }
    }

    public record TokenClaims(UUID subjectId, UUID sessionId, String role, String type) {}

    public String generateAccessToken(UUID subjectId, UUID sessionId, String role, String type) {
        Duration ttl = "ADMIN".equals(type) ? config.adminAccessTokenTtl() : config.accessTokenTtl();
        return generate(subjectId, sessionId, role, type, ttl);
    }

    private String generate(UUID subjectId, UUID sessionId, String role, String type, Duration ttl) {
        Instant now = Instant.now();
        var builder = new JWTClaimsSet.Builder()
                .subject(subjectId.toString())
                .claim("role", role)
                .claim("type", type)
                .issueTime(Date.from(now))
                .expirationTime(Date.from(now.plus(ttl)));

        if (sessionId != null) {
            builder.claim("sid", sessionId.toString());
        }

        try {
            SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), builder.build());
            jwt.sign(signer);
            return jwt.serialize();
        } catch (JOSEException e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    public TokenClaims parse(String token) {
        try {
            SignedJWT jwt = SignedJWT.parse(token);
            if (!jwt.verify(verifier)) return null;
            JWTClaimsSet claims = jwt.getJWTClaimsSet();
            if (claims.getExpirationTime() == null || claims.getExpirationTime().before(new Date())) return null;

            UUID subjectId = UUID.fromString(claims.getSubject());
            String sidStr = claims.getStringClaim("sid");
            UUID sessionId = sidStr != null ? UUID.fromString(sidStr) : null;
            String role = claims.getStringClaim("role");
            String type = claims.getStringClaim("type");
            return new TokenClaims(subjectId, sessionId, role, type);
        } catch (ParseException | JOSEException | IllegalArgumentException e) {
            return null;
        }
    }

    public Duration getRefreshTokenTtl() {
        return config.refreshTokenTtl();
    }
}
