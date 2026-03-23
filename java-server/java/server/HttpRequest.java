package server;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public class HttpRequest {
    private String method;
    private String path;
    private String version;
    private String body;
    private final byte[] rawBody;
    private final Map<String, String> headers;

    public HttpRequest(String rawRequest, byte[] rawBytes) {
        headers = new HashMap<>();
        parse(rawRequest);

        byte[] headerEnd = "\r\n\r\n".getBytes(StandardCharsets.UTF_8);
        int headerEndPos = indexOf(rawBytes, headerEnd);
        if (headerEndPos != -1) {
            int bodyStart = headerEndPos + 4;
            rawBody = Arrays.copyOfRange(rawBytes, bodyStart, rawBytes.length);
        } else {
            rawBody = new byte[0];
        }
    }

    private int indexOf(byte[] source, byte[] target) {
        outer: for (int i = 0; i <= source.length - target.length; i++) {
            for (int j = 0; j < target.length; j++) {
                if (source[i + j] != target[j])
                    continue outer;
            }
            return i;
        }
        return -1;
    }

    private void parse(String rawRequest) {
        String[] lines = rawRequest.split("\r\n");

        if (lines.length == 0)
            return;

        String[] firstLine = lines[0].split(" ");
        if (firstLine.length >= 3) {
            method = firstLine[0];
            path = firstLine[1];
            version = firstLine[2];
        }

        int i = 1;
        while (i < lines.length && !lines[i].isEmpty()) {
            String[] header = lines[i].split(": ", 2);
            if (header.length == 2) {
                headers.put(header[0], header[1]);
            }
            i++;
        }
        
        i++;
        StringBuilder bodyBuilder = new StringBuilder();
        while (i < lines.length) {
            bodyBuilder.append(lines[i]);
            i++;
        }
        body = bodyBuilder.toString();
    }

    public String getMethod() {
        return method;
    }

    public String getPath() {
        return path;
    }

    public String getVersion() {
        return version;
    }

    public String getBody() {
        return body;
    }

    public Map<String, String> getHeaders() {
        return headers;
    }

    public byte[] getRawBody() {
        return rawBody;
    }
}