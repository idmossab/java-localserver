import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        try {
            String jsonText = Files.readString(Path.of("java-server/config.json"));
            ParsingHandler parser = new ParsingHandler(jsonText);
            HashMap<String, Object> parsedConfig = parser.parse();

            ConfigLoader configLoader = new ConfigLoader(parsedConfig);
            configLoader.load();

            System.out.println("Server port is ready: " + configLoader.getPort());
        } catch (Exception e) {
            System.err.println("Main error: " + e.getMessage());
            System.exit(1);
        }
    }
}
