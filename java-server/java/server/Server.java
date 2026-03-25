package server;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import config.ParsingHandler;

public final class Server {
    public static final class Binding {
        private final String host;
        private final int port;
        private final Router router;

        public Binding(String host, int port, Router router) {
            this.host = host;
            this.port = port;
            this.router = router;
        }
    }

    private final String host;
    private final int port;
    private final Router router;

    public Server(String host, int port, Router router) {
        this.host = host;
        this.port = port;
        this.router = router;
    }

    public Server(ParsingHandler.ServerConfig config, Router router) {
        this(config.host, config.ports.get(0), router);
    }

    public void start() throws IOException {
        Selector selector = Selector.open();
        List<ServerSocketChannel> serverChannels = new ArrayList<>();

        try {
            registerBinding(selector, serverChannels, new Binding(host, port, router));
            runEventLoop(selector);
        } finally {
            for (ServerSocketChannel serverChannel : serverChannels) {
                serverChannel.close();
            }
            selector.close();
        }
    }

    public static void startAll(List<Binding> bindings) throws IOException {
        Selector selector = Selector.open();
        List<ServerSocketChannel> serverChannels = new ArrayList<>();

        try {
            for (Binding binding : bindings) {
                registerBinding(selector, serverChannels, binding);
            }
            runEventLoop(selector);
        } finally {
            for (ServerSocketChannel serverChannel : serverChannels) {
                serverChannel.close();
            }
            selector.close();
        }
    }

    private static void registerBinding(Selector selector, List<ServerSocketChannel> serverChannels, Binding binding)
            throws IOException {
        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(binding.host, binding.port));
        serverChannel.register(selector, SelectionKey.OP_ACCEPT, binding.router);
        serverChannels.add(serverChannel);

        System.out.println(
                "Server started on " + binding.host + ":" + binding.port + "\nURL: http://" + binding.host + ":"
                        + binding.port);
    }

    private static void runEventLoop(Selector selector) throws IOException {
        while (true) {
            selector.select();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isAcceptable()) {
                    handleAccept(key, selector);
                } else if (key.isReadable()) {
                    handleRead(key, (Router) key.attachment());
                }
            }
        }
    }

    static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        Router router = (Router) key.attachment();
        SocketChannel client = serverChannel.accept();
        if (client == null) {
            return;
        }
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, router);
    }

    static void handleRead(SelectionKey key, Router router) throws IOException {
        try (SocketChannel client = (SocketChannel) key.channel()) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            ByteArrayOutputStream rawBytes = new ByteArrayOutputStream();

            int bytesRead = 0;
            while ((bytesRead = client.read(buffer)) > 0) {
                buffer.flip();
                byte[] chunk = new byte[buffer.remaining()];
                buffer.get(chunk);
                rawBytes.write(chunk);
                buffer.clear();
            }

            byte[] requestBytes = rawBytes.toByteArray();
            if (requestBytes.length == 0) {
                client.close();
                return;
            }

            if (bytesRead < 0 && !client.isOpen()) {
                return;
            }

            String rawRequest = new String(requestBytes, StandardCharsets.UTF_8);
            HttpRequest request = new HttpRequest(rawRequest, requestBytes);
            HttpResponse response = router.route(request);

            ByteBuffer responseBuffer = ByteBuffer.wrap(response.build());
            client.write(responseBuffer);
            client.close();
        } catch (IOException e) {
            System.err.println("Error handling client: " + e.getMessage());
        }
    }
}
