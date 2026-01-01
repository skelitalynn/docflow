package com.docflow.document;

public enum DocFormat {
    RICH_TEXT(1),
    MARKDOWN(2),
    PLAIN(3);

    private final int code;

    DocFormat(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public static DocFormat fromCode(Integer code) {
        if (code == null) {
            return null;
        }
        for (DocFormat format : values()) {
            if (format.code == code) {
                return format;
            }
        }
        throw new IllegalArgumentException("Unknown DocFormat code: " + code);
    }
}
