package com.docflow.common;

public enum SystemRole {
    ADMIN(1),
    USER(2);

    private final int code;

    SystemRole(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static SystemRole fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (SystemRole role : values()) {
            if (role.code == code) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown SystemRole code: " + code);
    }
}
