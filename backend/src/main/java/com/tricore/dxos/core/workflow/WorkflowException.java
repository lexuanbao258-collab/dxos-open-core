package com.tricore.dxos.core.workflow;

import java.util.Objects;

/** A normalized failure. Provider exceptions must be translated at the adapter boundary. */
public final class WorkflowException extends RuntimeException {
    private final WorkflowErrorCode code;

    public WorkflowException(WorkflowErrorCode code) {
        // No provider cause or externally supplied message; no domain stack trace to expose.
        super(Objects.requireNonNull(code, "code").name(), null, false, false);
        this.code = code;
    }

    public WorkflowErrorCode code() {
        return code;
    }
}
