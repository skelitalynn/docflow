package com.docflow.common;

import java.util.Map;

public class BadRequestException extends RuntimeException {
    private final Map<String, Object> details;

    public BadRequestException(String message) {
        super(message);
        this.details = null;
    }

    public BadRequestException(String message, Map<String, Object> details) {
        super(message);
        this.details = details;
    }

    public Map<String, Object> getDetails() {
        return details;
    }
}
