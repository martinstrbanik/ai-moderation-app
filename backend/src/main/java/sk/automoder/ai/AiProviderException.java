package sk.automoder.ai;

/**
 * Exception thrown when a call to an AI provider fails.
 * {@code statusCode} is the HTTP status when available, otherwise 0.
 */
public class AiProviderException extends RuntimeException {

    private final int statusCode;

    public AiProviderException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int getStatusCode() {
        return statusCode;
    }
}