package com.infomaniak.sync;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Utils {

    /**
     * Méthode pour éviter les null pointer qui pourrait y avoir si le json est mal fait.
     * Comme ça l'application ne plantera pas, même si le résultat voulu ne sera pas affiché.
     * Methode Apple quoi, ne pas faire planté, mais ne pas afficher l'erreur.
     *
     * @param string chaine qu'on veut sanitize
     * @return chaine sanitize
     */
    public static String sanitize(String string) {
        if (string == null) {
            return "";
        } else {
            return string;
        }
    }

    private static final Pattern VALID_EMAIL_ADDRESS_REGEX =
            Pattern.compile("(?:[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*|\"(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21\\x23-\\x5b\\x5d-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])*\")@(?:(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\\.)+[a-z0-9](?:[a-z0-9-]*[a-z0-9])?|\\[(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?|[a-z0-9-]*[a-z0-9]:(?:[\\x01-\\x08\\x0b\\x0c\\x0e-\\x1f\\x21-\\x5a\\x53-\\x7f]|\\\\[\\x01-\\x09\\x0b\\x0c\\x0e-\\x7f])+)])", Pattern.CASE_INSENSITIVE);

    public static boolean validateEmail(String emailStr) {
        Matcher matcher = VALID_EMAIL_ADDRESS_REGEX.matcher(emailStr);
        return matcher.find();
    }
}
