package utils;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

public final class Cookie {
    private static final DateTimeFormatter EXPIRES_FORMATTER =
            DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).withZone(ZoneOffset.UTC);

    private final String name;
    private final String value;
    private String path = "/";
    private Long maxAgeSeconds;
    private Instant expiresAt;
    private boolean httpOnly = true;
    private boolean secure;
    private String sameSite = "Lax";

    public Cookie(String name, String value) {
        validateName(name);
        this.name = name;
        this.value = value == null ? "" : value;
    }

    public static Map<String, String> parseRequestHeader(String headerValue) {
        if (headerValue == null || headerValue.isBlank()) {
            return Collections.emptyMap();
        }

        Map<String, String> cookies = new LinkedHashMap<>();
        String[] pairs = headerValue.split(";");
        for (String pair : pairs) {
            String trimmed = pair.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            int separator = trimmed.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            String name = trimmed.substring(0, separator).trim();
            String value = trimmed.substring(separator + 1).trim();
            if (!name.isEmpty()) {
                cookies.put(name, value);
            }
        }
        return cookies;
    }

    public static Cookie session(String name, String value, long maxAgeSeconds) {
        return new Cookie(name, value).path("/").maxAgeSeconds(maxAgeSeconds).httpOnly(true).sameSite("Lax");
    }

    public String getName() {
        return name;
    }

    public String getValue() {
        return value;
    }

    public Cookie path(String path) {
        this.path = (path == null || path.isBlank()) ? "/" : path;
        return this;
    }

    public Cookie maxAgeSeconds(long maxAgeSeconds) {
        this.maxAgeSeconds = maxAgeSeconds;
        return this;
    }

    public Cookie expiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
        return this;
    }

    public Cookie httpOnly(boolean httpOnly) {
        this.httpOnly = httpOnly;
        return this;
    }

    public Cookie secure(boolean secure) {
        this.secure = secure;
        return this;
    }

    public Cookie sameSite(String sameSite) {
        this.sameSite = (sameSite == null || sameSite.isBlank()) ? "Lax" : sameSite;
        return this;
    }

    public String toSetCookieHeader() {
        StringBuilder builder = new StringBuilder();
        builder.append(name).append('=').append(value);
        builder.append("; Path=").append(path);
        if (maxAgeSeconds != null) {
            builder.append("; Max-Age=").append(maxAgeSeconds.longValue());
        }
        if (expiresAt != null) {
            builder.append("; Expires=").append(EXPIRES_FORMATTER.format(expiresAt));
        }
        if (httpOnly) {
            builder.append("; HttpOnly");
        }
        if (secure) {
            builder.append("; Secure");
        }
        if (sameSite != null && !sameSite.isBlank()) {
            builder.append("; SameSite=").append(sameSite);
        }
        return builder.toString();
    }

    private static void validateName(String name) {
        Objects.requireNonNull(name, "name");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Cookie name cannot be blank");
        }
        for (int index = 0; index < name.length(); index++) {
            char current = name.charAt(index);
            if (Character.isWhitespace(current) || current == ';' || current == ',' || current == '=') {
                throw new IllegalArgumentException("Cookie name contains invalid characters");
            }
        }
    }
}
