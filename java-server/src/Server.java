import config.ConfigLoader;
import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

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
        try {
            // 1- get port from config
            int port = config.getPort();
            System.out.println("Starting server on port " + port);
            // 02- open socket and listen for connections not blocking
            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.bind(new java.net.InetSocketAddress(port));
            // 3- set up selector and register server channel for accept events
            Selector selector = Selector.open();
            serverChannel.register(selector, java.nio.channels.SelectionKey.OP_ACCEPT);
            // 4- main loop to handle events
            while (true) {
                // 5- wait for events
                selector.select();
                
                //open and regester in server 
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();
                while (iter.hasNext()) {
                    SelectionKey key = iter.next();

                    if (key.isAcceptable()) {
                        // new client from browser
                        System.out.println("✅ New client is trying to connect!");

                        // accept connect and trying
                        ServerSocketChannel server = (ServerSocketChannel) key.channel();
                        SocketChannel client = server.accept();
                        client.configureBlocking(false);
                        client.register(selector, SelectionKey.OP_READ);
                    }
                    iter.remove(); // remove key for not work abput them another time
                }

            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        // TODO: graceful shutdown
    }
}
