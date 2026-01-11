package com.docflow.common;

public enum NotificationType {
    SHARE(1),
    COMMENT_REPLY(2),
    MENTION(3),
    TASK_ASSIGNED(4),
    TASK_COMPLETED(5),
    DOC_EDIT(6),
    COMMENT(7),
    COMMENT_STATUS(8);

    private final int code;

    NotificationType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static NotificationType fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (NotificationType type : values()) {
            if (type.code == code) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown NotificationType code: " + code);
    }
}
