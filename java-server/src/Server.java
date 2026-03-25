import config.ConfigLoader;
import config.ParsingHandler;

public final class Server {
    private final ConfigLoader config;
    private final Router router;
    private final CGIHandler cgiHandler;

    public Server(ConfigLoader config, Router router, CGIHandler cgiHandler) {
        this.config = config;
        this.router = router;
        this.cgiHandler = cgiHandler;
    }

    public void start() {
        for (ParsingHandler.ServerConfig serverConfig : config.getServers()) {
            for (int port : serverConfig.ports) {
                System.out.println("Server will start on "
                        + serverConfig.host + ":" + port);
            }
        }
    }
}
