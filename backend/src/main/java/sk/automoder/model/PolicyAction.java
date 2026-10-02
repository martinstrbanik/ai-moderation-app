package sk.automoder.model;

/** Verdict the moderation assigns to a text. */
public enum PolicyAction {
    // content is fine
    ALLOW,
    // content violates the policy - block it
    BLOCK
}