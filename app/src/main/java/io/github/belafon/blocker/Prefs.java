package io.github.belafon.blocker;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import java.net.IDN;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/** All persistent state of the app. */
public final class Prefs {
    private static final String FILE = "blocker";
    private static final String KEY_DOMAINS = "domains";
    private static final String KEY_DEFAULTS_ADDED = "defaults_added";
    private static final String KEY_ONBOARDING_DONE = "onboarding_done";
    private static final String KEY_FILTER_ENABLED = "filter_enabled";
    private static final String KEY_PASSWORD_HASH = "password_hash";
    private static final String KEY_PASSWORD_SALT = "password_salt";

    private static final int PBKDF2_ITERATIONS = 20000;

    private Prefs() {}

    public static SharedPreferences get(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // --- Blocked domains ---

    public static Set<String> getDomains(Context c) {
        return new TreeSet<>(get(c).getStringSet(KEY_DOMAINS, Collections.emptySet()));
    }

    /** Adds DefaultDomains on the very first start only, so removed defaults don't come back. */
    public static void addDefaultDomainsOnce(Context c) {
        SharedPreferences sp = get(c);
        if (sp.getBoolean(KEY_DEFAULTS_ADDED, false)) return;
        Set<String> domains = new HashSet<>(getDomains(c));
        domains.addAll(DefaultDomains.DOMAINS);
        sp.edit().putStringSet(KEY_DOMAINS, domains).putBoolean(KEY_DEFAULTS_ADDED, true).commit();
    }

    public static boolean isDomainsKey(String key) {
        return KEY_DOMAINS.equals(key);
    }

    public static void addDomain(Context c, String domain) {
        Set<String> domains = new HashSet<>(getDomains(c));
        domains.add(domain);
        get(c).edit().putStringSet(KEY_DOMAINS, domains).apply();
    }

    public static void removeDomain(Context c, String domain) {
        Set<String> domains = new HashSet<>(getDomains(c));
        domains.remove(domain);
        get(c).edit().putStringSet(KEY_DOMAINS, domains).apply();
    }

    /**
     * Turns user input like "https://www.Example.com/path" into "example.com".
     * Returns null when the input is not a domain.
     */
    public static String normalizeDomain(String input) {
        String s = input.trim().toLowerCase(Locale.ROOT);
        int scheme = s.indexOf("://");
        if (scheme >= 0) s = s.substring(scheme + 3);
        for (char end : new char[] {'/', '?', '#'}) {
            int i = s.indexOf(end);
            if (i >= 0) s = s.substring(0, i);
        }
        int at = s.lastIndexOf('@');
        if (at >= 0) s = s.substring(at + 1);
        int colon = s.indexOf(':');
        if (colon >= 0) s = s.substring(0, colon);
        while (s.startsWith("*.")) s = s.substring(2);
        if (s.startsWith("www.")) s = s.substring(4);
        if (s.endsWith(".")) s = s.substring(0, s.length() - 1);
        try {
            s = IDN.toASCII(s).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return s.matches("[a-z0-9-]+(\\.[a-z0-9-]+)+") ? s : null;
    }

    // --- Help cards ---

    public static boolean isOnboardingDone(Context c) {
        return get(c).getBoolean(KEY_ONBOARDING_DONE, false);
    }

    public static void setOnboardingDone(Context c) {
        get(c).edit().putBoolean(KEY_ONBOARDING_DONE, true).apply();
    }

    // --- Filter on/off (what the user wants, not whether the VPN is currently up) ---

    public static boolean isFilterEnabled(Context c) {
        return get(c).getBoolean(KEY_FILTER_ENABLED, false);
    }

    public static void setFilterEnabled(Context c, boolean enabled) {
        get(c).edit().putBoolean(KEY_FILTER_ENABLED, enabled).commit();
    }

    // --- Password. "Locked" means a password is set. ---

    public static boolean hasPassword(Context c) {
        return get(c).contains(KEY_PASSWORD_HASH);
    }

    public static void setPassword(Context c, String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        get(c).edit()
                .putString(KEY_PASSWORD_SALT, Base64.encodeToString(salt, Base64.NO_WRAP))
                .putString(KEY_PASSWORD_HASH, Base64.encodeToString(hash(password, salt), Base64.NO_WRAP))
                .commit();
    }

    /** Sets a long random password that is never shown, so only the emergency code can unlock. */
    public static void setRandomPassword(Context c) {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 64; i++) {
            sb.append(EmergencyCode.ALPHABET.charAt(random.nextInt(EmergencyCode.ALPHABET.length())));
        }
        setPassword(c, sb.toString());
    }

    public static boolean checkPassword(Context c, String password) {
        SharedPreferences sp = get(c);
        String hash = sp.getString(KEY_PASSWORD_HASH, null);
        String salt = sp.getString(KEY_PASSWORD_SALT, null);
        if (hash == null || salt == null) return true;
        byte[] actual = hash(password, Base64.decode(salt, Base64.NO_WRAP));
        return MessageDigest.isEqual(actual, Base64.decode(hash, Base64.NO_WRAP));
    }

    public static void clearPassword(Context c) {
        get(c).edit().remove(KEY_PASSWORD_HASH).remove(KEY_PASSWORD_SALT).commit();
    }

    private static byte[] hash(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 256);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
