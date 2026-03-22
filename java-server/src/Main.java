import config.ParsingHandler;
import config.ConfigLoader;
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

            System.out.println("Config file read successfully. Host: " + parser.host + ", Ports: " + parser.ports);
        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            System.exit(1);
        }
    }
}
