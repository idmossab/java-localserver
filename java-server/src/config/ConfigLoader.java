package config;

public final class ConfigLoader {
    private final ParsingHandler parser;
    private int port;

    public ConfigLoader(ParsingHandler parser) {
        this.parser = parser;
    }

    public void load() {
        try {
            if (parser.ports == null || parser.ports.isEmpty()) {
                throw new IllegalArgumentException("ports is empty");
            }

            this.port = parser.ports.get(0);
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
