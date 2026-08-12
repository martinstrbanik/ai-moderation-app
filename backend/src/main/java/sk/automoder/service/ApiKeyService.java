package sk.automoder.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import sk.automoder.dto.ApiKeyRequest;
import sk.automoder.dto.ApiKeyResponse;
import sk.automoder.exception.NotFoundException;
import sk.automoder.model.ApiKey;
import sk.automoder.repository.ApiKeyRepository;
import sk.automoder.security.AesGcmEncryptor;

import java.util.List;

@Service
public class ApiKeyService {

    private final ApiKeyRepository repository;
    private final AesGcmEncryptor encryptor;

    public ApiKeyService(ApiKeyRepository repository, AesGcmEncryptor encryptor) {
        this.repository = repository;
        this.encryptor = encryptor;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyResponse> list() {
        return repository.findByTenantId(PolicyService.DEFAULT_TENANT).stream()
                .map(k -> ApiKeyResponse.of(k, mask(k)))
                .toList();
    }

    @Transactional
    public ApiKeyResponse create(ApiKeyRequest request) {
        ApiKey apiKey = new ApiKey();
        apiKey.setTenantId(PolicyService.DEFAULT_TENANT);
        apiKey.setLabel(request.label());
        apiKey.setEncryptedKey(encryptor.encrypt(request.key()));
        ApiKey saved = repository.save(apiKey);
        return ApiKeyResponse.of(saved, ApiKeyResponse.mask(request.key()));
    }

    @Transactional
    public void delete(Long id) {
        repository.delete(requireKey(id));
    }

    /** Decrypted key - used internally when calling OpenRouter. */
    @Transactional(readOnly = true)
    public String decrypted(Long id) {
        ApiKey key = requireKey(id);
        return encryptor.decrypt(key.getEncryptedKey());
    }

    private ApiKey requireKey(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> NotFoundException.of("API key", id));
    }

    private String mask(ApiKey apiKey) {
        // only the encrypted value is available from the database -
        // the last plaintext characters cannot be derived, return a generic mask
        return "****••••";
    }
}