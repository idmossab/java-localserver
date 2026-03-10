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
        // TODO: initialize selector, bind ports, register channels
    }

    public void stop() {
        // TODO: graceful shutdown
    }
}
