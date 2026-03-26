package config;

import java.util.List;

public final class RegexValidator {

    // constants  regex
    public static final String IPV4_REGEX =
        "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)$";
    public static final String ERROR_PAGE_CODE_REGEX =
        "^(400|403|404|405|413|500)$";
    public static final String ERROR_PAGE_PATH_REGEX =
        "^errors/[A-Za-z0-9._/-]+\\.html$";

    private RegexValidator() {
        // private constructor =>  instance
    }

    // validate host (exact IPv4 only)
    public static boolean isValidHost(String host) {
        if (host == null) return false;
        return host.matches(IPV4_REGEX);
    }

    // validate ports list
    public static boolean isValidPorts(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) return false;
        for (Integer p : ports) {
            if (p == null || p < 1 || p > 65535) return false;
        }
        return true;
    }
    // validate set_timeout_seconds
    public static boolean isValidTimeoutSeconds(int timeoutSeconds) {
        return timeoutSeconds > 0;
    }
    // validate path (basic)
    public static boolean isValidPath(String path) {
        if (path == null || path.isEmpty()) return false;
        return path.startsWith("/"); //  path    "/"
    }

    // validate methods list
    public static boolean isValidMethods(List<String> methods) {
        if (methods == null || methods.isEmpty()) return false;
        for (String m : methods) {
            if (m == null) return false;
            switch (m) {
                case "GET":
                case "POST":
                case "PUT":
                case "DELETE":
                case "PATCH":
                    break;
                default:
                    return false;
            }
        }
        return true;
    }

    public static boolean isValidErrorPageCode(String code) {
        if (code == null) return false;
        return code.matches(ERROR_PAGE_CODE_REGEX);
    }

    public static boolean isValidErrorPagePath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.contains("..")) return false;
        return path.matches(ERROR_PAGE_PATH_REGEX);
    }
}
