
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ConfigLoader {
    private final String path;
    private int port;

    public ConfigLoader(String path) {
        this.path = path;
    }

    public void load() {
        try {
            String content = Files.readString(Paths.get(path));
            Pattern pattern = Pattern.compile("port\\s*=\\s*(\\d+)");
            Matcher matcher = pattern.matcher(content);
            if (matcher.find()) {
                this.port = Integer.parseInt(matcher.group(1));
                System.out.println("Config Loaded: Found Port " + this.port);
            } else {
                throw new Exception("Port not found in config.json");
            }

        } catch (Exception e) {
            System.err.println("Error loading config: " + e.getMessage());
            // If config loading fails, we can't start the server, so exit with an error
            // code
            System.exit(1);
        }
    }

    public int getPort() {
        return port;
    }
}