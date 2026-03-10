public final class ConfigLoader {
    private final String path;

    public ConfigLoader(String path) {
        this.path = path;
    }

    public void load() {
        // TODO: parse config.json and validate
    }

    public String getPath() {
        return path;
    }
}
