package com.eventprocessor.reliable_event_processor.service;

import com.eventprocessor.reliable_event_processor.dto.ApiKeyResponse;
import com.eventprocessor.reliable_event_processor.dto.CreateApiKeyRequest;
import com.eventprocessor.reliable_event_processor.dto.CreateApiKeyResponse;
import com.eventprocessor.reliable_event_processor.entity.ApiKey;
import com.eventprocessor.reliable_event_processor.entity.ApiKeyStatus;
import com.eventprocessor.reliable_event_processor.exception.ResourceNotFoundException;
import com.eventprocessor.reliable_event_processor.repository.ApiKeyRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyService.class);
    private static final String KEY_PREFIX_HEADER = "rf_live_";
    private static final int RANDOM_BYTES_COUNT = 24; // 48 hex chars
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final ApiKeyRepository apiKeyRepository;
    private final OperationalAuditService auditService;

    public ApiKeyService(ApiKeyRepository apiKeyRepository,
                         OperationalAuditService auditService) {
        this.apiKeyRepository = apiKeyRepository;
        this.auditService = auditService;
    }

    @Transactional
    public CreateApiKeyResponse createApiKey(String tenantId, CreateApiKeyRequest request, String principal) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("Tenant ID is required to issue API credentials");
        }

        byte[] randomBytes = new byte[RANDOM_BYTES_COUNT];
        SECURE_RANDOM.nextBytes(randomBytes);
        String randomHex = HexFormat.of().formatHex(randomBytes);

        String secretKey = KEY_PREFIX_HEADER + randomHex;
        String keyPrefix = secretKey.substring(0, 16); // "rf_live_" + 8 hex chars
        String secretHash = hashSecret(secretKey);

        ApiKey apiKey = new ApiKey(
                tenantId,
                request.name().trim(),
                keyPrefix,
                secretHash,
                ApiKeyStatus.ACTIVE,
                request.expiresAt(),
                principal != null ? principal : "SYSTEM"
        );

        ApiKey saved = apiKeyRepository.save(apiKey);

        auditService.recordAuditLog(
                tenantId,
                "API_KEY_CREATE",
                keyPrefix,
                "SUCCESS",
                null,
                principal,
                "Generated new API key '" + saved.getName() + "' with prefix: " + keyPrefix
        );

        log.info("API key generated successfully [id={}, tenantId={}, prefix={}, actor={}]",
                saved.getId(), tenantId, keyPrefix, principal);

        return new CreateApiKeyResponse(
                saved.getId(),
                saved.getName(),
                saved.getKeyPrefix(),
                secretKey,
                saved.getStatus(),
                saved.getCreatedAt() != null ? saved.getCreatedAt() : OffsetDateTime.now(),
                saved.getExpiresAt(),
                "Store this secret securely. It will not be shown again."
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> listApiKeys(String tenantId) {
        return apiKeyRepository.findByTenantId(tenantId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public ApiKeyResponse revokeApiKey(String tenantId, Long keyId, String principal) {
        ApiKey apiKey = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + keyId));

        if (apiKey.getStatus() != ApiKeyStatus.REVOKED) {
            apiKey.setStatus(ApiKeyStatus.REVOKED);
            apiKey.setRevokedAt(OffsetDateTime.now());
            apiKeyRepository.save(apiKey);

            auditService.recordAuditLog(
                    tenantId,
                    "API_KEY_REVOKE",
                    apiKey.getKeyPrefix(),
                    "SUCCESS",
                    null,
                    principal,
                    "Revoked API key: " + apiKey.getName()
            );

            log.info("API key revoked [id={}, tenantId={}, prefix={}, actor={}]",
                    apiKey.getId(), tenantId, apiKey.getKeyPrefix(), principal);
        }

        return toResponse(apiKey);
    }

    @Transactional
    public CreateApiKeyResponse rotateApiKey(String tenantId, Long keyId, String principal) {
        ApiKey oldKey = apiKeyRepository.findByIdAndTenantId(keyId, tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("API key not found with id: " + keyId));

        // Revoke the old key
        oldKey.setStatus(ApiKeyStatus.REVOKED);
        oldKey.setRevokedAt(OffsetDateTime.now());
        apiKeyRepository.save(oldKey);

        auditService.recordAuditLog(
                tenantId,
                "API_KEY_ROTATE",
                oldKey.getKeyPrefix(),
                "SUCCESS",
                null,
                principal,
                "Rotated API key (revoked old key: " + oldKey.getKeyPrefix() + ")"
        );

        // Generate the new key with the same name and expiry
        CreateApiKeyRequest newKeyRequest = new CreateApiKeyRequest(oldKey.getName(), oldKey.getExpiresAt());
        return createApiKey(tenantId, newKeyRequest, principal);
    }

    @Transactional
    public Optional<ApiKey> authenticateApiKey(String rawApiKey) {
        if (rawApiKey == null || !rawApiKey.startsWith(KEY_PREFIX_HEADER) || rawApiKey.length() < 24) {
            return Optional.empty();
        }

        String keyPrefix = rawApiKey.substring(0, Math.min(rawApiKey.length(), 16));
        List<ApiKey> candidates = apiKeyRepository.findByKeyPrefixAndStatus(keyPrefix, ApiKeyStatus.ACTIVE);

        byte[] candidateHash = computeHashBytes(rawApiKey);

        for (ApiKey candidate : candidates) {
            byte[] storedHash = HexFormat.of().parseHex(candidate.getSecretHash());
            if (MessageDigest.isEqual(candidateHash, storedHash)) {
                if (candidate.isExpired()) {
                    candidate.setStatus(ApiKeyStatus.EXPIRED);
                    apiKeyRepository.save(candidate);
                    log.warn("API key {} is expired", candidate.getKeyPrefix());
                    return Optional.empty();
                }

                candidate.setLastUsedAt(OffsetDateTime.now());
                apiKeyRepository.save(candidate);
                return Optional.of(candidate);
            }
        }

        return Optional.empty();
    }

    public static String hashSecret(String secretKey) {
        byte[] hash = computeHashBytes(secretKey);
        return HexFormat.of().formatHex(hash);
    }

    private static byte[] computeHashBytes(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest algorithm not available", e);
        }
    }

    private ApiKeyResponse toResponse(ApiKey entity) {
        return new ApiKeyResponse(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getLastUsedAt(),
                entity.getExpiresAt(),
                entity.getRevokedAt(),
                entity.getCreatedBy()
        );
    }
}
