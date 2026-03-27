package server;

import config.ParsingHandler;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import utils.Cookie;
import utils.Session;

public class Router {

    private final String wwwRoot;
    private final FileUploadHandler uploadHandler;
    private final ParsingHandler.ServerConfig config;
    private final config.ConfigLoader configLoader;
    private final CGIHandler cgiHandler;
    private final Session.Store sessionStore;

    public Router(String wwwRoot, ParsingHandler.ServerConfig config, config.ConfigLoader configLoader) {
        this.wwwRoot = wwwRoot;
        this.uploadHandler = new FileUploadHandler(wwwRoot + "/uploads", this);
        this.config = config;
        this.configLoader = configLoader;
        this.cgiHandler = new CGIHandler(configLoader);
        this.sessionStore = new Session.Store("sessionId", 30 * 60 * 1000L); // 30 minutes TTL
    }

    public HttpResponse route(HttpRequest request) {
        String method = request.getMethod();
        String path = request.getPath();

        if (method == null || path == null) {
            return errorResponse(HttpResponse.BAD_REQUEST, "400");
        }

        // CGI handling
        if (path.endsWith(".cgi")) {
            RequestContext ctx = new RequestContext(request.getMethod(), request.getPath(), request.getHeaders(), request.getRawBody(), "localhost", 8080);
            Path scriptPath = Paths.get(wwwRoot, path.substring(1));
            String extension = path.substring(path.lastIndexOf('.') + 1);
            RequestContext.RouteMatch routeMatch = new RequestContext.RouteMatch(path, scriptPath, extension, null, "", null, null, true, false);
            ctx.setRouteMatch(routeMatch);
            try {
                cgiHandler.execute(ctx);
                RequestContext.HttpResponse response = ctx.getResponse();
                HttpResponse httpResponse = new HttpResponse();
                httpResponse.setStatus(response.getStatusCode());
                for (Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
                    for (String value : entry.getValue()) {
                        httpResponse.addHeader(entry.getKey(), value);
                    }
                }
                httpResponse.setBody(response.getBody(), "text/html");
                return httpResponse;
            } catch (Exception e) {
                return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
            }
        }

        // Check maxBodySize
        String contentLength = request.getHeaders().get("Content-Length");
        if (contentLength != null) {
            try {
                long bodySize = Long.parseLong(contentLength);
                if (bodySize > config.clientBodyLimitBytes) {
                    return errorResponse(HttpResponse.CONTENT_TOO_LARGE, "413");
                }
            } catch (NumberFormatException e) {
                return errorResponse(HttpResponse.BAD_REQUEST, "400");
            }
        }

        return switch (method) {
            case "GET" -> path.equals("/profile") ? handleProfile(request) : handleGet(path, request);
            case "POST" -> path.equals("/login") ? handleLogin(request) : handlePost(path, request);
            case "DELETE" -> path.equals("/logout") ? handleLogout(request) : handleDelete(path);
            default -> errorResponse(HttpResponse.METHOD_NOT_ALLOWED, "405");
        };
    }

    // ============ Auth & Session ============
    private HttpResponse handleLogin(HttpRequest request) {
        HttpResponse response = new HttpResponse();

        String body = request.getBody();
        String username = extractParam(body, "username");

        if (username != null && !username.isEmpty()) {
            Session session = sessionStore.create(System.currentTimeMillis());
            session.putAttribute("username", username);

            Cookie cookie = sessionStore.buildSessionCookie(session, false);

            response.setStatus(HttpResponse.OK);
            response.addCookie(cookie);
            response.setBody(
                    ("{\"message\": \"Login successful!\", \"username\": \"" + username + "\"}").getBytes(),
                    "application/json");
        } else {
            return errorResponse(HttpResponse.BAD_REQUEST, "400");
        }

        return response;
    }

    private HttpResponse handleProfile(HttpRequest request) {
        HttpResponse response = new HttpResponse();

        String cookieHeader = request.getHeaders().get("Cookie");
        Map<String, String> cookies = Cookie.parseRequestHeader(cookieHeader);
        Session session = sessionStore.resolve(cookies, System.currentTimeMillis());

        if (session != null) {
            String username = session.getAttribute("username");
            response.setStatus(HttpResponse.OK);
            response.setBody(
                    ("{\"username\": \"" + username + "\", \"message\": \"Welcome " + username + "!\"}").getBytes(),
                    "application/json");
        } else {
            return errorResponse(HttpResponse.FORBIDDEN, "403");
        }

        return response;
    }

    private HttpResponse handleLogout(HttpRequest request) {
        HttpResponse response = new HttpResponse();

        String cookieHeader = request.getHeaders().get("Cookie");
        Map<String, String> cookies = Cookie.parseRequestHeader(cookieHeader);
        Session session = sessionStore.resolve(cookies, System.currentTimeMillis());

        if (session != null) {
            // Note: The new Session doesn't have destroy, but since it's resolved, it's already managed.
            // To logout, we can just send an expired cookie.

            Cookie cookie = new Cookie("sessionId", "").path("/").maxAgeSeconds(0L).httpOnly(true);
            response.addCookie(cookie);

            response.setStatus(HttpResponse.OK);
            response.setBody("{\"message\": \"Logout successful!\"}".getBytes(), "application/json");
        } else {
            return errorResponse(HttpResponse.FORBIDDEN, "403");
        }

        return response;
    }

    private String extractParam(String body, String param) {
        if (body == null || body.isEmpty())
            return null;
        String[] pairs = body.split("&");
        for (String pair : pairs) {
            String[] keyValue = pair.split("=");
            if (keyValue.length == 2 && keyValue[0].equals(param)) {
                return keyValue[1];
            }
        }
        return null;
    }

    // ============ GET ============
    private HttpResponse handleGet(String path, HttpRequest request) {
        HttpResponse response = new HttpResponse();

        if (path.equals("/")) {
            path = "/index.html";
        }

        Path filePath = Paths.get(wwwRoot + path);

        if (Files.exists(filePath) && !Files.isDirectory(filePath)) {
            try {
                byte[] content = Files.readAllBytes(filePath);
                String mimeType = getMimeType(path);

                response.setStatus(HttpResponse.OK);
                response.setBody(content, mimeType);
            } catch (IOException e) {
                return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
            }
        } else {
            return errorResponse(HttpResponse.NOT_FOUND, "404");
        }

        return response;
    }

    // ============ POST ============
    private HttpResponse handlePost(String path, HttpRequest request) {
        if (path.equals("/upload")) {
            return uploadHandler.handle(request);
        }

        HttpResponse response = new HttpResponse();
        response.setStatus(HttpResponse.OK);
        response.setBody(("{\"message\": \"Data received!\", \"path\": \"" + path + "\"}").getBytes(),
                "application/json");
        return response;
    }

    // ============ DELETE ============
    private HttpResponse handleDelete(String path) {
        Path filePath = Paths.get(wwwRoot + path);

        if (!filePath.toAbsolutePath().startsWith(Paths.get(wwwRoot).toAbsolutePath())) {
            return errorResponse(HttpResponse.FORBIDDEN, "403");
        }

        if (!Files.exists(filePath)) {
            return errorResponse(HttpResponse.NOT_FOUND, "404");
        }

        try {
            Files.delete(filePath);

            HttpResponse response = new HttpResponse();
            response.setStatus(HttpResponse.OK);
            response.setBody(("{\"message\": \"File deleted!\", \"path\": \"" + path + "\"}").getBytes(),
                    "application/json");
            return response;
        } catch (IOException e) {
            return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
        }
    }

    // ============ Error Handling ============
    public HttpResponse errorResponse(int statusCode, String code) {
        HttpResponse response = new HttpResponse();
        response.setStatus(statusCode);

        String errorPagePath = config.errorPages.get(code);
        if (errorPagePath != null) {
            Path filePath = Paths.get(errorPagePath);
            if (Files.exists(filePath)) {
                try {
                    response.setBody(Files.readAllBytes(filePath), "text/html");
                    return response;
                } catch (IOException e) {
                    return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
                }
            }
        }
        // Default error
        response.setBody(("<h1>" + statusCode + " Error</h1>").getBytes(StandardCharsets.UTF_8), "text/html");
        return response;
    }

    // ============ Utils ============
    private String getMimeType(String path) {
        if (path.endsWith(".html"))
            return "text/html";
        if (path.endsWith(".css"))
            return "text/css";
        if (path.endsWith(".js"))
            return "application/javascript";
        if (path.endsWith(".png"))
            return "image/png";
        if (path.endsWith(".jpg"))
            return "image/jpeg";
        if (path.endsWith(".json"))
            return "application/json";
        if (path.endsWith(".ico"))
            return "image/x-icon";
        return "text/plain";
    }
}