package com.tricore.dxos.application.workflow;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

/**
 * Generates opaque instance IDs at the trusted application boundary.
 * Does not perform authorization or expose Workflow endpoints.
 *
 * A collision is never treated as proof of an existing caller-visible instance.
 * Callers must not automatically retry an uncertain commit outcome.
 */
public final class WorkflowInstanceIdGenerator {
    private static final int ENTROPY_BYTES = 24;
    private final SecureRandom random;

    public WorkflowInstanceIdGenerator() {
        this(new SecureRandom());
    }

    WorkflowInstanceIdGenerator(SecureRandom random) {
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * 192 random bits in unpadded base64url (32 characters), prefixed for diagnostics.
     * Every character conforms to the existing Workflow instance ID contract.
     */
    public String nextId() {
        byte[] bytes = new byte[ENTROPY_BYTES];
        random.nextBytes(bytes);
        return "wf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
