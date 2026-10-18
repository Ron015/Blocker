package io.github.belafon.blocker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * The emergency code that unlocks everything without the password.
 *
 * It is intentionally visible in the app and here in the source (and in README.md). The point is
 * not secrecy but effort: copying is disabled, so you have to write it on paper and type it back.
 * Replace it with your own (64 random characters from ALPHABET) before building if you like.
 */
public final class EmergencyCode {
    public static final String CODE =
            "eE!7HjLi%Lg4%e3Hhq^Fs2eCXyTCnng5w9Xj6HqVY$a)6qqD($8qwF5f3KDWCyu^";

    /**
     * Characters a code may use: letters, digits and !@#$%^&*(). Look-alikes (0 O o 1 l I) are
     * left out so the code can be copied from paper without guessing.
     */
    public static final String ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789!@#$%^&*()";

    private EmergencyCode() {}

    /** Drops spaces and line breaks. Everything else, including letter case, must match. */
    public static String normalize(String s) {
        return s.replaceAll("\\s", "");
    }

    public static int length() {
        return normalize(CODE).length();
    }

    public static boolean matches(String input) {
        return MessageDigest.isEqual(
                normalize(input).getBytes(StandardCharsets.US_ASCII),
                normalize(CODE).getBytes(StandardCharsets.US_ASCII));
    }

    /** The code in groups of 4 characters, 4 groups per line, easy to copy onto paper. */
    public static String formatted() {
        String code = normalize(CODE);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) sb.append(i % 16 == 0 ? '\n' : ' ');
            sb.append(code, i, Math.min(i + 4, code.length()));
        }
        return sb.toString();
    }
}
