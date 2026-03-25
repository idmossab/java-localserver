import config.ConfigLoader;
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
            ConfigLoader configLoader = new ConfigLoader(parser);
            configLoader.load();
            Router router = new Router(configLoader);
            CGIHandler cgiHandler = new CGIHandler(configLoader);
            Server server = new Server(configLoader, router, cgiHandler);

            System.out.println("Config file read successfully. Servers: " + parser.servers.size());
            server.start();
        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            System.exit(1);
        }
    }
}
