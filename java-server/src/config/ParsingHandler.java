package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParsingHandler {
    private final String jsonText;
    public String host;
    public List<Integer> ports;
    public int clientBodyLimitBytes;
    public Map<String, String> errorPages;
    public Map<String, Map<String, String>> routes;
    public Map<String, String> cgi;

    public ParsingHandler(String jsonText) {
        this.jsonText = jsonText;
        parse();
    }

    public void parse() {
        try {
            Object root = new JsonParser(jsonText).parseValueAndFinish();
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("config root must be object");
            }

            Map<?, ?> config = (Map<?, ?>) root;
            host = readHost(config);
            ports = readPorts(config.get("ports"));
            clientBodyLimitBytes = readClientBodyLimit(config.get("client_body_limit_bytes"));
            errorPages = readErrorPages(config.get("error_pages"));
            routes = readRoutes(config.get("routes"));
            cgi = readCGI(config.get("cgi"));
        } catch (Exception e) {
            System.err.println("Parsing error: " + e.getMessage());
            System.exit(1);
        }
    }

    private String readHost(Map<?, ?> config) {
        Object value = config.get("host");
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("host must be string");
        }
        return (String) value;
    }

    private List<Integer> readPorts(Object value) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("ports must be array");
        }

        List<?> rawPorts = (List<?>) value;
        if (rawPorts.isEmpty()) {
            throw new IllegalArgumentException("ports must not be empty");
        }

        ArrayList<Integer> parsedPorts = new ArrayList<>();
        for (Object port : rawPorts) {
            if (!(port instanceof Integer)) {
                throw new IllegalArgumentException("ports must contain only numbers");
            }
            parsedPorts.add((Integer) port);
        }
        return parsedPorts;
    }

    private int readClientBodyLimit(Object value) {
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("client_body_limit_bytes must be number");
        }
        return (Integer) value;
    }

private Map<String, String> readErrorPages(Object value) {
    if (!(value instanceof Map<?, ?>)) {
        throw new IllegalArgumentException("error_pages must be object");
    }
    Map<?, ?> raw = (Map<?, ?>) value;
    Map<String, String> pages = new HashMap<>();
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new IllegalArgumentException("error_pages keys and values must be strings");
        }
        pages.put((String) entry.getKey(), (String) entry.getValue());
    }
    return pages;
}

private Map<String, Map<String, String>> readRoutes(Object value) {
    if (!(value instanceof Map<?, ?>)) {
        throw new IllegalArgumentException("routes must be object");
    }

    Map<?, ?> raw = (Map<?, ?>) value;
    Map<String, Map<String, String>> map = new HashMap<>();

    for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (!(entry.getKey() instanceof String)) {
            throw new IllegalArgumentException("route path must be string");
        }

        String routePath = (String) entry.getKey();
        Object methodsObj = entry.getValue();

        if (!(methodsObj instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("methods for route " + routePath + " must be object");
        }

        Map<?, ?> rawMethods = (Map<?, ?>) methodsObj;
        Map<String, String> methodMap = new HashMap<>();
        for (Map.Entry<?, ?> me : rawMethods.entrySet()) {
            if (!(me.getKey() instanceof String) || !(me.getValue() instanceof String)) {
                throw new IllegalArgumentException("method keys and handlers must be strings");
            }
            methodMap.put((String) me.getKey(), (String) me.getValue());
        }

        map.put(routePath, methodMap);
    }

    return map;
}

private Map<String, String> readCGI(Object value) {
    if (!(value instanceof Map<?, ?>)) {
        throw new IllegalArgumentException("cgi must be object");
    }

    Map<?, ?> raw = (Map<?, ?>) value;
    Map<String, String> map = new HashMap<>();

    for (Map.Entry<?, ?> entry : raw.entrySet()) {
        if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
            throw new IllegalArgumentException("cgi keys and values must be strings");
        }
        map.put((String) entry.getKey(), (String) entry.getValue());
    }

    return map;
}

}
