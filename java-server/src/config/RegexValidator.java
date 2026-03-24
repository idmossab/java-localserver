package config;

import java.util.List;

public final class RegexValidator {

    // constants  regex
    public static final String IPV4_REGEX =
        "^((25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.|$)){4}$";
    public static final String HOSTNAME_REGEX =
        "^[a-zA-Z0-9.-]+$";

    private RegexValidator() {
        // private constructor =>  instance
    }

    // validate host (IPv4  hostname)
    public static boolean isValidHost(String host) {
        if (host == null) return false;
        return host.matches(IPV4_REGEX) || host.matches(HOSTNAME_REGEX);
    }

    // validate ports list
    public static boolean isValidPorts(List<Integer> ports) {
        if (ports == null || ports.isEmpty()) return false;
        for (Integer p : ports) {
            if (p == null || p < 1 || p > 65535) return false;
        }
        return true;
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
}