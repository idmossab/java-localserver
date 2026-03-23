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
import java.util.Iterator;
import java.util.Set;

public class Main {

    static ConfigLoader config;
    static Router router;

    public static void main(String[] args) throws IOException {
        config = new ConfigLoader("config.json");
        router = new Router(config.getWwwRoot(), config);

        ServerSocketChannel serverChannel = ServerSocketChannel.open();
        serverChannel.configureBlocking(false);
        serverChannel.bind(new InetSocketAddress(config.getPort()));
        Selector selector = Selector.open();

        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        System.out.println("Server started on port " + config.getPort() + "\nURL: http://localhost:" + config.getPort());

        while (true) {
            selector.select();

            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> iter = selectedKeys.iterator();

            while (iter.hasNext()) {
                SelectionKey key = iter.next();
                iter.remove();

                if (key.isAcceptable()) {
                    handleAccept(serverChannel, selector);
                } else if (key.isReadable()) {
                    handleRead(key);
                }
            }
        }
    }

    static void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        SocketChannel client = serverChannel.accept();
        client.configureBlocking(false);
        client.register(selector, SelectionKey.OP_READ);
    }

    static void handleRead(SelectionKey key) throws IOException {
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