package com.infomaniak.sync;

import static com.infomaniak.sync.Utils.sanitize;

public class InfomaniakUser {
    private int id;
    private int user_id;
    private String login;
    private String email;
    private String firstname;
    private String lastname;
    private String display_name;
    private boolean otp;
    private int validated_at;
    private int last_login_at;
    private int administration_last_login_at;
    private boolean invalid_email;
    private String avatar;
    private String locale;
    private int language_id;
    private String timezone;
    private InfomaniakCountry country;

    public int getId() {
        return id;
    }

    public int getUser_id() {
        return user_id;
    }

    public String getLogin() {
        return sanitize(login);
    }

    public String getEmail() {
        return sanitize(email);
    }

    public String getFirstname() {
        return sanitize(firstname);
    }

    public String getLastname() {
        return sanitize(lastname);
    }

    public void setDisplay_name(String display_name) {
        this.display_name = display_name;
    }

    public String getDisplay_name() {
        return sanitize(display_name);
    }

    public boolean isOtp() {
        return otp;
    }

    public int getValidated_at() {
        return validated_at;
    }

    public int getLast_login_at() {
        return last_login_at;
    }

    public int getAdministration_last_login_at() {
        return administration_last_login_at;
    }

    public boolean isInvalid_email() {
        return invalid_email;
    }

    public String getAvatar() {
        return sanitize(avatar);
    }

    public String getLocale() {
        return sanitize(locale);
    }

    public int getLanguage_id() {
        return language_id;
    }

    public String getTimezone() {
        return sanitize(timezone);
    }

    public InfomaniakCountry getCountry() {
        if (country == null) {
            country = new InfomaniakCountry();
        }
        return country;
    }

    public class InfomaniakCountry {
        private int id = 72;
        private String name = "FRANCE";

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
