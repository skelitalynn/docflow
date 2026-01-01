package com.docflow.common;

public enum CommentStatus {
    ACTIVE(1),
    DELETED(2);

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
