package config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {
    private final ParsingHandler parser;
    private List<Integer> ports;

    public ConfigLoader(ParsingHandler parser) {
        this.parser = parser;
    }

    public void load() {
        try {
            if (parser.ports == null || parser.ports.isEmpty()) {
                throw new IllegalArgumentException("ports is empty");
            }

            this.ports = new ArrayList<>(parser.ports);
            System.out.println("ConfigLoader loaded port: " + ports);
        } catch (Exception e) {
            System.err.println("ConfigLoader error: " + e.getMessage());
            System.exit(1);
        }
    }

    public List<Integer> getPorts() {
        return ports;
    }
}
