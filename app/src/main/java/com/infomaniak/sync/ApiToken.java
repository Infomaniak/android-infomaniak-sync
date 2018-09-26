package com.infomaniak.sync;

public class ApiToken {
    private String access_token;
    private String refresh_token;
    private String token_type;
    private int expires_in;
    private int user_id;
    private String scope;

    public String getAccess_token() {
        return access_token;
    }

    public String getRefresh_token() {
        return refresh_token;
    }

    public String getToken_type() {
        return token_type;
    }

    public int getExpires_in() {
        return expires_in;
    }

    public int getUser_id() {
        return user_id;
    }

    public String getScope() {
        return scope;
    }
}
