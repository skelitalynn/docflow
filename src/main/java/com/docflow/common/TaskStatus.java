package com.docflow.common;

public enum TaskStatus {
    TODO(1),
    IN_PROGRESS(2),
    DONE(3);

    private final int code;

    TaskStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static TaskStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (TaskStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown TaskStatus code: " + code);
    }
}
