package utils;

import java.util.HashMap;
import java.util.Map;

public class Cookie {
    private final String name;
    private final String value;
    private final String path;
    private int maxAge;
    private final boolean httpOnly;

    public Cookie(String name, String value) {
        this.name     = name;
        this.value    = value;
        this.path     = "/";
        this.maxAge   = -1;
        this.httpOnly = true;
    }

    // Build Set-Cookie header value
    public String toHeaderValue() {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append("=").append(value);

        if (path != null) {
            sb.append("; Path=").append(path);
        }
        if (maxAge >= 0) {
            sb.append("; Max-Age=").append(maxAge);
        }
        if (httpOnly) {
            sb.append("; HttpOnly");
        }

        return sb.toString();
    }

    public static Map<String, String> parse(String cookieHeader) {
        Map<String, String> cookies = new HashMap<>();
        if (cookieHeader == null || cookieHeader.isEmpty()) return cookies;

        String[] pairs = cookieHeader.split(";");
        for (String pair : pairs) {
            pair = pair.trim();
            int eq = pair.indexOf("=");
            if (eq != -1) {
                String k = pair.substring(0, eq).trim();
                String v = pair.substring(eq + 1).trim();
                cookies.put(k, v);
            }
        }
        return cookies;
    }

    // Setters
    public void setMaxAge(int maxAge)      { this.maxAge  = maxAge; }

    // Getters
    public String getName()  { return name; }
    public String getValue() { return value; }
}