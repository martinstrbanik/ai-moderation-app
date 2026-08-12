package sk.automoder.model;

/** Action the moderation should take for a given policy. */
public enum PolicyAction {
    // content is fine
    ALLOW,
    // content is suspicious - flag it
    FLAG,
    // content violates the policy - block it
    BLOCK
}