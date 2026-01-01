package com.docflow.common;

public enum DocRole {
    OWNER(1, 3),
    EDITOR(2, 2),
    VIEWER(3, 1);

    private final int code;
    private final int priority;

    DocRole(int code, int priority) {
        this.code = code;
        this.priority = priority;
    }

    public int getCode() {
        return code;
    }

    public boolean atLeast(DocRole required) {
        return this.priority >= required.priority;
    }

    public static DocRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown DocRole code: " + code);
    }
}
