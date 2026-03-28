package server;

import config.ParsingHandler;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Server {
    private static final class BindingGroup {
        private final List<Binding> bindings;

        private BindingGroup(List<Binding> bindings) {
            this.bindings = bindings;
        }
    }

    public static final class Binding {
        private final String name;
        private final String host;
        private final int port;
        private final Router router;

        public Binding(String name, String host, int port, Router router) {
            this.name = name;
            this.host = host;
            this.port = port;
            this.router = router;
        }
    }

    private final String name;
    private final String host;
    private final int port;
    private final Router router;

    public Server(String name, String host, int port, Router router) {
        this.name = name;
        this.host = host;
        this.port = port;
        this.router = router;
    }

    public Server(ParsingHandler.ServerConfig config, Router router) {
        this(config.name, config.host, config.ports.get(0), router);
    }

    public void start() throws IOException {
        List<Binding> bindings = new ArrayList<>();
        bindings.add(new Binding(name, host, port, router));

        startAll(bindings);
    }

    public static void startAll(List<Binding> bindings) throws IOException {
        Selector selector = Selector.open();
        List<ServerSocketChannel> serverChannels = new ArrayList<>();

        try {
            Map<String, List<Binding>> grouped = new HashMap<>();

            for (Binding b : bindings) {
                String key = b.host + ":" + b.port;
                grouped.computeIfAbsent(key, k -> new ArrayList<>()).add(b);
            }
            registerBinding(selector, serverChannels, grouped);
            runEventLoop(selector);
        } finally {
            for (ServerSocketChannel serverChannel : serverChannels) {
                serverChannel.close();
            }
            selector.close();
        }
    }

    private static void registerBinding(Selector selector, List<ServerSocketChannel> serverChannels,
            Map<String, List<Binding>> grouped)
            throws IOException {

        for (Map.Entry<String, List<Binding>> entry : grouped.entrySet()) {
            String[] parts = entry.getKey().split(":");
            String host = parts[0];
            int port = Integer.parseInt(parts[1]);

            List<Binding> samePortBindings = entry.getValue();
            BindingGroup group = new BindingGroup(samePortBindings);

            ServerSocketChannel serverChannel = ServerSocketChannel.open();
            serverChannel.configureBlocking(false);
            serverChannel.socket().setReuseAddress(true);
            serverChannel.bind(new InetSocketAddress(host, port));

            serverChannel.register(selector, SelectionKey.OP_ACCEPT, group);

            serverChannels.add(serverChannel);

        }
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
                    BindingGroup group = (BindingGroup) key.attachment();
                    handleRead(key, group.bindings);
                }
            }
        }
    }

    static void handleAccept(SelectionKey key, Selector selector) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();

        BindingGroup group = (BindingGroup) key.attachment();

        SocketChannel client = serverChannel.accept();
        if (client == null)
            return;

        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ, group);
    }

    static void handleRead(SelectionKey key, List<Binding> bindings) throws IOException {
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

            String hostHeader = request.getHeaders().get("Host");

            Binding selected = bindings.get(0);

            for (Binding b : bindings) {
                if (hostHeader != null && hostHeader.contains(b.name)) {
                    selected = b;
                    break;
                }
            }

            HttpResponse response = selected.router.route(request);

            ByteBuffer responseBuffer = ByteBuffer.wrap(response.build());
            client.write(responseBuffer);
            client.close();
        } catch (Exception e) {
            System.err.println("Error handling client: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
