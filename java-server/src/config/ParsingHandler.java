package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParsingHandler {
    public static final class ServerConfig {
        public String host;
        public List<Integer> ports;
        public int clientBodyLimitBytes;
        public Map<String, String> errorPages;
        public Map<String, List<String>> routes;
        public Map<String, String> cgi;
    }

    private final String jsonText;
    public List<ServerConfig> servers;

    public ParsingHandler(String jsonText) {
        this.jsonText = jsonText;
        this.servers = new ArrayList<>();
        parse();
    }

    public void parse() {
        try {
            Object root = new JsonParser(jsonText).parseValueAndFinish();
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("config root must be object");
            }

            Map<?, ?> config = (Map<?, ?>) root;
            Object serversValue = config.get("servers");
            if (!(serversValue instanceof List<?>)) {
                throw new IllegalArgumentException("servers must be array");
            }

            List<?> rawServers = (List<?>) serversValue;
            if (rawServers.isEmpty()) {
                throw new IllegalArgumentException("servers must not be empty");
            }

            servers.clear();
            for (Object serverValue : rawServers) {
                if (!(serverValue instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("each server must be object");
                }

                Map<?, ?> serverMap = (Map<?, ?>) serverValue;
                ServerConfig serverConfig = new ServerConfig();
                serverConfig.host = readHost(serverMap);
                serverConfig.ports = readPorts(serverMap.get("ports"));
                serverConfig.clientBodyLimitBytes = readClientBodyLimit(serverMap.get("client_body_limit_bytes"));
                serverConfig.errorPages = readErrorPages(serverMap.get("error_pages"));
                serverConfig.routes = readRoutes(serverMap.get("routes"));
                serverConfig.cgi = readCGI(serverMap.get("cgi"));
                validateConfig(serverConfig);
                servers.add(serverConfig);
            }
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

    private Map<String, List<String>> readRoutes(Object value) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("routes must be array");
        }

        List<?> rawRoutes = (List<?>) value;
        Map<String, List<String>> map = new HashMap<>();

        for (Object obj : rawRoutes) {
            if (!(obj instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("each route must be object");
            }

            Map<?, ?> routeMap = (Map<?, ?>) obj;

            Object pathObj = routeMap.get("path");
            Object methodsObj = routeMap.get("methods");

            if (!(pathObj instanceof String)) {
                throw new IllegalArgumentException("path must be string");
            }

            if (!(methodsObj instanceof List<?>)) {
                throw new IllegalArgumentException("methods must be array");
            }

            String path = (String) pathObj;
            List<?> rawMethods = (List<?>) methodsObj;

            List<String> methods = new ArrayList<>();

            for (Object m : rawMethods) {
                if (!(m instanceof String)) {
                    throw new IllegalArgumentException("method must be string");
                }
                methods.add((String) m);
            }

            map.put(path, methods);
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

    private void validateConfig(ServerConfig serverConfig) {
        if (!RegexValidator.isValidHost(serverConfig.host)) {
            throw new IllegalArgumentException("host format is invalid");
        }

        if (!RegexValidator.isValidPorts(serverConfig.ports)) {
            throw new IllegalArgumentException("ports are invalid");
        }

        for (Map.Entry<String, List<String>> route : serverConfig.routes.entrySet()) {
            if (!RegexValidator.isValidPath(route.getKey())) {
                throw new IllegalArgumentException("route path is invalid: " + route.getKey());
            }
            if (!RegexValidator.isValidMethods(route.getValue())) {
                throw new IllegalArgumentException("route methods are invalid for path: " + route.getKey());
            }
        }
    }
}
