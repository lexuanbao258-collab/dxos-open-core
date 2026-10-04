package com.tricore.dxos.request.attachment.domain;

public class AttachmentException extends RuntimeException {
    private final int status;
    private final String code;

    public AttachmentException(int status, String code, String message) {
        this(status, code, message, null);
    }

    public AttachmentException(int status, String code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    public int getStatus() { return status; }
    public String getCode() { return code; }
}
