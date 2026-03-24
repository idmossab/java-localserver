package config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ConfigLoader {
    private final ParsingHandler parser;
    private ParsingHandler.ServerConfig server;
    private String host;
    private List<Integer> ports;
    private Map<String, List<String>> routes;


    public ConfigLoader(ParsingHandler parser) {
        this.parser = parser;
    }

    public void load() {
        try {
            if (parser.servers == null || parser.servers.isEmpty()) {
                throw new IllegalArgumentException("no servers found in config");
            }

            this.server = parser.servers.get(0);
            this.host = server.host;
            this.ports = new ArrayList<>(server.ports);
            this.routes = server.routes;

            // System.out.println("ConfigLoader loaded port: " + ports);
        } catch (Exception e) {
            System.err.println("ConfigLoader error: " + e.getMessage());
            System.exit(1);
        }
    }

    public String getHost() {
        return host;
    }
    public List<Integer> getPorts() {
        return ports;
    }
    public Map<String, List<String>> getRoutes() {
        return routes;
    }
}
