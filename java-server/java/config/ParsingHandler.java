package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ParsingHandler {
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8080;
    private static final String DEFAULT_NAME_PREFIX = "server";

    public static final class ServerConfig {
        public String host;
        public List<Integer> ports;
        public String name;
        public int clientBodyLimitBytes;
        public int timeoutSeconds;
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
            Set<String> seenServerNames = new HashSet<>();
            for (Object serverValue : rawServers) {
                if (!(serverValue instanceof Map<?, ?>)) {
                    throw new IllegalArgumentException("each server must be object");
                }

                Map<?, ?> serverMap = (Map<?, ?>) serverValue;
                checkUnknownKeys(serverMap, Set.of("host", "ports", "name", "client_body_limit_bytes", "timeout_seconds", "error_pages", "routes", "cgi"), "server config");
                ServerConfig serverConfig = new ServerConfig();
                serverConfig.host = readHost(serverMap);
                serverConfig.ports = readPorts(serverMap.get("ports"));
                serverConfig.name = readName(serverMap.get("name"));
                if (serverConfig.name == null) {
                    serverConfig.name = DEFAULT_NAME_PREFIX + (servers.size() + 1);
                    System.err.println("Warning: missing field 'name' in server config. Using default value '" + serverConfig.name + "'.");
                }
                if (serverConfig.name != null && !seenServerNames.add(serverConfig.name)) {
                    throw new IllegalArgumentException("server names must not be duplicated");
                }
                serverConfig.clientBodyLimitBytes = readClientBodyLimit(serverMap.get("client_body_limit_bytes"));
                serverConfig.timeoutSeconds = readTimeoutSeconds(serverMap.get("timeout_seconds"));
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
        if (value == null) {
            System.err.println("Warning: missing field 'host' in server config. Using default value '" + DEFAULT_HOST + "'.");
            return DEFAULT_HOST;
        }
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("host must be string");
        }
        String host = (String) value;
        String[] parts = host.split("\\.");

        if (parts.length != 4) {
            throw new IllegalArgumentException("host must be valid IPv4");
        }

        for (String part : parts) {
            try {
                int number = Integer.parseInt(part);
                if (number < 0 || number > 255) {
                    throw new IllegalArgumentException("host octets must be between 0 and 255");
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("host must contain only numbers separated by dots");
            }
        }

        return host;
    }

    private List<Integer> readPorts(Object value) {
        if (value == null) {
            ArrayList<Integer> defaultPorts = new ArrayList<>();
            defaultPorts.add(DEFAULT_PORT);
            System.err.println("Warning: missing field 'ports' in server config. Using default value '" + defaultPorts + "'.");
            return defaultPorts;
        }
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("ports must be array");
        }

        List<?> rawPorts = (List<?>) value;
        if (rawPorts.isEmpty()) {
            throw new IllegalArgumentException("ports must not be empty");
        }

        ArrayList<Integer> parsedPorts = new ArrayList<>();
        Set<Integer> seenPorts = new HashSet<>();
        for (Object port : rawPorts) {
            if (!(port instanceof Integer)) {
                throw new IllegalArgumentException("ports must contain only numbers");
            }
            Integer portNumber = (Integer) port;
            if (!seenPorts.add(portNumber)) {
                throw new IllegalArgumentException("ports must not contain duplicate numbers");
            }
            parsedPorts.add(portNumber);
        }
        return parsedPorts;
    }

    private String readName(Object value) {
        if (value == null) return null;
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("name must be string");
        }
        return (String) value;
    }

    private int readClientBodyLimit(Object value) {
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("client_body_limit_bytes must be number");
        }
        return (Integer) value;
    }

    private int readTimeoutSeconds(Object value) {
        if (!(value instanceof Integer)) {
            throw new IllegalArgumentException("set_timeout_seconds must be number");
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
            String code = (String) entry.getKey();
            String path = (String) entry.getValue();

            if (!RegexValidator.isValidErrorPageCode(code)) {
                throw new IllegalArgumentException("error_pages code is invalid: " + code);
            }
            if (!RegexValidator.isValidErrorPagePath(path)) {
                throw new IllegalArgumentException("error_pages path is invalid for code " + code + ": " + path);
            }

            pages.put(code, path);
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
            checkUnknownKeys(routeMap, Set.of("path", "methods"), "route config");

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

    private void checkUnknownKeys(Map<?, ?> obj, Set<String> allowedKeys, String context) {
        for (Object key : obj.keySet()) {
            if (key instanceof String && !allowedKeys.contains(key)) {
                System.err.println("Warning: unknown field '" + key + "' in " + context + ". It will be ignored.");
            }
        }
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
            String extension = (String) entry.getKey();
            String path = (String) entry.getValue();

            if (!RegexValidator.isValidCGIExtension(extension)) {
                throw new IllegalArgumentException("cgi extension is invalid: " + extension);
            }
            if (!RegexValidator.isValidCGIExecutablePath(path)) {
                throw new IllegalArgumentException("cgi path is invalid for extension " + extension + ": " + path);
            }

            map.put(extension, path);
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

        if(!RegexValidator.isValidTimeoutSeconds(serverConfig.timeoutSeconds)) {
            throw new IllegalArgumentException("timeout_seconds must be positive");
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
