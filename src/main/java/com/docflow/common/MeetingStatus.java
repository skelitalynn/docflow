package com.docflow.common;

public enum MeetingStatus {
    ACTIVE(1),
    ENDED(2);

    private final int code;

    MeetingStatus(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static MeetingStatus fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (MeetingStatus status : values()) {
            if (status.code == code) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown MeetingStatus code: " + code);
    }
}
