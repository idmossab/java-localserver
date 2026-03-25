package config;

import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {
    private final ParsingHandler parser;
    private List<ParsingHandler.ServerConfig> servers;

    public ConfigLoader(ParsingHandler parser) {
        this.parser = parser;
    }

    public void load() {
        try {
            if (parser.servers == null || parser.servers.isEmpty()) {
                throw new IllegalArgumentException("servers is empty");
            }

            // copy all servers
            this.servers = new ArrayList<>(parser.servers);

        } catch (Exception e) {
            System.err.println("ConfigLoader error: " + e.getMessage());
            System.exit(1);
        }
    }

    public List<ParsingHandler.ServerConfig> getServers() {
        return servers;
    }
}