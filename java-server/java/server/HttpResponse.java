package server;

import java.util.HashMap;
import java.util.Map;
import utils.Cookie;

public class HttpResponse {
    private int statusCode;
    private String statusMessage;
    private final Map<String, String> headers;
    private byte[] body;

    public HttpResponse() {
        headers = new HashMap<>();
        headers.put("Server", "JavaServer/1.0");
    }

    public static final int OK = 200;
    public static final int NOT_FOUND = 404;
    public static final int BAD_REQUEST = 400;
    public static final int FORBIDDEN = 403;
    public static final int CONTENT_TOO_LARGE = 413;
    public static final int METHOD_NOT_ALLOWED = 405;
    public static final int INTERNAL_SERVER_ERROR = 500;

    public void setStatus(int code) {
        this.statusCode = code;
        statusMessage = switch (code) {
            case OK -> "OK";
            case BAD_REQUEST -> "Bad Request";
            case FORBIDDEN -> "Forbidden";
            case NOT_FOUND -> "Not Found";
            case METHOD_NOT_ALLOWED -> "Method Not Allowed";
            case CONTENT_TOO_LARGE -> "Content Too Large";
            case INTERNAL_SERVER_ERROR -> "Internal Server Error";
            default -> "Unknown";
        };
    }

    public void setBody(byte[] body, String contentType) {
        this.body = body;
        headers.put("Content-Type", contentType + "; charset=UTF-8");
        headers.put("Content-Length", String.valueOf(body.length));
    }

    public void addHeader(String key, String value) {
        headers.put(key, value);
    }

    public byte[] build() {
        StringBuilder sb = new StringBuilder();

        sb.append("HTTP/1.1 ")
                .append(statusCode)
                .append(" ")
                .append(statusMessage)
                .append("\r\n");

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            sb.append(entry.getKey())
                    .append(": ")
                    .append(entry.getValue())
                    .append("\r\n");
        }

        sb.append("\r\n");

        byte[] headerBytes = sb.toString().getBytes();

        if (body == null)
            return headerBytes;

        byte[] response = new byte[headerBytes.length + body.length];

        System.arraycopy(headerBytes, 0, response, 0, headerBytes.length);
        System.arraycopy(body, 0, response, headerBytes.length, body.length);

        return response;
    }

    public void addCookie(Cookie cookie) {
        headers.put("Set-Cookie", cookie.toHeaderValue());
    }
}