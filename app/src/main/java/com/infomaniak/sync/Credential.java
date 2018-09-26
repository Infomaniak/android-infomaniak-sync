package com.infomaniak.sync;

import static com.infomaniak.sync.Utils.sanitize;

public class Credential {

    private int id;
    private String access_token;
    private String refresh_token;
    private String firstname;
    private String lastname;
    private String email;
    private String avatar;
    private long date;
    private ErrorAPI errorAPI;

    public Credential(ErrorAPI errorAPI) {
        this.errorAPI = errorAPI;
    }

    public Credential(int id, String access_token, String refresh_token, String firstname, String lastname, String email, String avatar, long date) {
        this.id = id;
        this.access_token = access_token;
        this.refresh_token = refresh_token;
        this.firstname = firstname;
        this.lastname = lastname;
        this.email = email;
        this.avatar = avatar;
        this.date = date;
    }

    public int getId() {
        return id;
    }

    public String getAccess_token() {
        return sanitize(access_token);
    }

    public String getRefresh_token() {
        return sanitize(refresh_token);
    }

    public String getFirstname() {
        return sanitize(firstname);
    }

    public String getLastname() {
        return sanitize(lastname);
    }

    public String getEmail() {
        return sanitize(email);
    }

    public String getAvatar() {
        if (avatar == null) {
            avatar = "https://etickets.infomaniak.com/images/avatar-infomaniak.png";
        }
        return avatar;
    }

    public long getDate() {
        return date;
    }

    public ErrorAPI getErrorAPI() {
        return errorAPI;
    }
}
