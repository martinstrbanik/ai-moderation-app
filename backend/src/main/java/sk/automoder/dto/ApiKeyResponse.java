package sk.automoder.dto;

import sk.automoder.model.ApiKey;

import java.time.Instant;

/**
 * Response for an API key - never contains plaintext, only a mask.
 */
public record ApiKeyResponse(
        Long id,
        String label,
        String maskedKey,
        Instant createdAt,
        Instant lastUsedAt
) {
    public static ApiKeyResponse of(ApiKey apiKey, String maskedKey) {
        return new ApiKeyResponse(
                apiKey.getId(),
                apiKey.getLabel(),
                maskedKey,
                apiKey.getCreatedAt(),
                apiKey.getLastUsedAt()
        );
    }

    /** Keeps the last 4 characters of the key visible, masks the rest. */
    public static String mask(String plainKey) {
        if (plainKey == null || plainKey.length() < 8) {
            return "****";
        }
        int keep = 4;
        return "****" + plainKey.substring(plainKey.length() - keep);
    }
}