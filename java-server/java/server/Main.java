package server;

import config.ParsingHandler;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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

            System.out.println("Config file read successfully. Servers: " + parser.servers.size());

            List<ParsingHandler.ServerConfig> serverConfigs = configLoader.getServers();
            Set<String> bindings = new HashSet<>();
            List<Server.Binding> serverBindings = new ArrayList<>();

            for (ParsingHandler.ServerConfig serverConfig : serverConfigs) {
                Router router = new Router(serverConfig.rootDirectory, serverConfig);

                for (Integer port : serverConfig.ports) {
                    String binding = serverConfig.host + ":" + port;
                    if (!bindings.add(binding)) {
                        throw new IllegalArgumentException("Duplicate server binding: " + binding);
                    }

                    serverBindings.add(new Server.Binding(serverConfig.host, port, router));
                }
            }

            Server.startAll(serverBindings);
        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            System.exit(1);
        }
    }
}
