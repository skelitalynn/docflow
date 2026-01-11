package com.docflow.common;

public enum CommentStatus {
    OPEN(1),
    RESOLVED(2),
    DELETED(3);

    private final int code;

    CommentStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static CommentStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (CommentStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown CommentStatus code: " + code);
    }
}
