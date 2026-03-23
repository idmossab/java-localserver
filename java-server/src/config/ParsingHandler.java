package config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ParsingHandler {
    private final String jsonText;
    public String host;
    public List<Integer> ports;

    public ParsingHandler(String jsonText) {
        this.jsonText = jsonText;
        this.ports = new ArrayList<>();
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
}
