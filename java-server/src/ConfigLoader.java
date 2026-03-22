import java.util.ArrayList;
import java.util.HashMap;

public final class ConfigLoader {
    private final HashMap<String, Object> parsedConfig;
    private int port;

    public ConfigLoader(HashMap<String, Object> parsedConfig) {
        this.parsedConfig = parsedConfig;
    }

    public void load() {
        try {
            @SuppressWarnings("unchecked")
            ArrayList<Integer> ports = (ArrayList<Integer>) parsedConfig.get("ports");
            Integer defaultServer = (Integer) parsedConfig.get("default_server");

            if (ports == null || ports.isEmpty()) {
                throw new IllegalArgumentException("ports is empty");
            }
            if (defaultServer == null) {
                throw new IllegalArgumentException("default_server is missing");
            }
            if (defaultServer < 0 || defaultServer >= ports.size()) {
                throw new IllegalArgumentException("default_server is out of range");
            }

            this.port = ports.get(defaultServer);
            System.out.println("ConfigLoader loaded port: " + port);
        } catch (Exception e) {
            System.err.println("ConfigLoader error: " + e.getMessage());
            System.exit(1);
        }
    }

    public int getPort() {
        return port;
    }
}
