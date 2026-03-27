package config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public final class ConfigLoader {
    private final ParsingHandler parser;
    private List<ParsingHandler.ServerConfig> servers;
    private ParsingHandler.ServerConfig serverConfig;
    private long cgiTimeoutMillis = 3000L;
    private Path projectRoot = Paths.get(".");

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
            this.serverConfig = servers.get(0); // assume single server

        } catch (Exception e) {
            System.err.println("ConfigLoader error: " + e.getMessage());
            System.exit(1);
        }
    }

    public List<ParsingHandler.ServerConfig> getServers() {
        return servers;
    }

    public String getCgiInterpreter(String extension) {
        if (serverConfig.cgi != null) {
            return serverConfig.cgi.get(extension);
        }
        return null;
    }

    public long getCgiTimeoutMillis() {
        return cgiTimeoutMillis;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }
}