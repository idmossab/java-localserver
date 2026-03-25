package server;

import config.ParsingHandler;
import java.nio.file.Files;
import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            String jsonText = Files.readString(Path.of("config.json"));
            ParsingHandler parser = new ParsingHandler(jsonText);
            config.ConfigLoader configLoader = new config.ConfigLoader(parser);
            configLoader.load();

            ParsingHandler.ServerConfig serverConfig = configLoader.getServers().get(0);
            Router router = new Router("www", serverConfig);
            Server server = new Server(serverConfig, router);

            System.out.println("Config file read successfully. Servers: " + parser.servers.size());
            server.start();
        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            System.exit(1);
        }
    }
}
