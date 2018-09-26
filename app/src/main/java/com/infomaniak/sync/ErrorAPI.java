package com.infomaniak.sync;

import static com.infomaniak.sync.Utils.sanitize;

public class ErrorAPI {
    private int error_code;
    private String error_type;
    private String error;
    private String reason;

    public int getError_code() {
        return error_code;
    }

    public String getError_type() {
        return sanitize(error_type);
    }

    public String getError() {
        return sanitize(error);
    }

    public String getReason() {
        return sanitize(reason);
    }
}
