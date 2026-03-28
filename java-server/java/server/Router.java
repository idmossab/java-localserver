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
        if (isCGI(path)) {
            return executeCGI(Paths.get(wwwRoot, path.substring(1)), getExtension(path), request);
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

        // Extract and validate route
        RouteMatch routeMatch;
        try {
            routeMatch = extractRoute(request);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Route not found")) {
                return errorResponse(HttpResponse.NOT_FOUND, "404");
            } else if (e.getMessage().contains("Method not allowed")) {
                return errorResponse(HttpResponse.METHOD_NOT_ALLOWED, "405");
            }
            return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
        }

        return switch (method) {
            case "GET" -> path.equals("/profile") ? handleProfile(request) : handleGet(path, request, routeMatch);
            case "POST" -> path.equals("/login") ? handleLogin(request) : handlePost(path, request);
            case "DELETE" -> path.equals("/logout") ? handleLogout(request) : handleDelete(path, routeMatch);
            default -> errorResponse(HttpResponse.METHOD_NOT_ALLOWED, "405");
        };
    }

    private HttpResponse executeCGI(Path scriptPath, String extension, HttpRequest request) {
        RequestContext ctx = new RequestContext(request.getMethod(), request.getPath(), request.getHeaders(), request.getRawBody(), "localhost", 8080);
        RequestContext.RouteMatch routeMatch = new RequestContext.RouteMatch(request.getPath(), scriptPath, extension, null, "", null, null, true, false);
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

    private boolean isCGI(String path) {
        String ext = getExtension(path);
        return ext != null && config.cgi.containsKey(ext);
    }

    private String getExtension(String path) {
        int dot = path.lastIndexOf('.');
        if (dot == -1) return null;
        return path.substring(dot);
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
    private HttpResponse handleGet(String path, HttpRequest request, RouteMatch routeMatch) {
        HttpResponse response = new HttpResponse();

        String effectiveRoot = routeMatch.routeConfig.rootDirectory != null ? routeMatch.routeConfig.rootDirectory : wwwRoot;
        String relativePath = path.substring(routeMatch.path.length());
        if (relativePath.isEmpty()) relativePath = "/";
        Path filePath = Paths.get(effectiveRoot + relativePath);
        System.out.println("Effective root: " + effectiveRoot + ", Route path: " + routeMatch.path + ", Requested path: " + path + ", Relative path: " + relativePath + ", Resolved file path: " + filePath);

        // Handle directory fallback using route or server default file
        if (Files.isDirectory(filePath)) {
            boolean allowAutoindex = routeMatch.routeConfig.autoindex || config.autoindex;
            if (allowAutoindex) {
                return generateDirectoryListing(filePath, relativePath, effectiveRoot);
            }

            String routeDefault = routeMatch.routeConfig.defaultFile != null ? routeMatch.routeConfig.defaultFile : config.defaultFile;
            Path indexFilePath = filePath.resolve(routeDefault);
            if (routeDefault != null && Files.exists(indexFilePath) && !Files.isDirectory(indexFilePath)) {
                try {
                    byte[] content = Files.readAllBytes(indexFilePath);
                    String mimeType = getMimeType(routeDefault);

                    response.setStatus(HttpResponse.OK);
                    response.setBody(content, mimeType);
                    return response;
                } catch (IOException e) {
                    return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
                }
            }

            return errorResponse(HttpResponse.NOT_FOUND, "404");
        }

        // Try directory slash normalization (path may be a directory without trailing slash)
        if (!Files.exists(filePath)) {
            Path slashPath = Paths.get(effectiveRoot + (relativePath.endsWith("/") ? relativePath : relativePath + "/"));
            if (Files.isDirectory(slashPath)) {
                return handleGet(path.endsWith("/") ? path : path + "/", request, routeMatch);
            }
            return errorResponse(HttpResponse.NOT_FOUND, "404");
        }

        if (!Files.isDirectory(filePath)) {
            // Check if it's a CGI script
            if (isCGI(relativePath)) {
                return executeCGI(filePath, getExtension(relativePath), request);
            }

            try {
                byte[] content = Files.readAllBytes(filePath);
                String mimeType = getMimeType(relativePath);

                response.setStatus(HttpResponse.OK);
                response.setBody(content, mimeType);
                return response;
            } catch (IOException e) {
                return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
            }
        }

        return errorResponse(HttpResponse.NOT_FOUND, "404");
    }

    private HttpResponse generateDirectoryListing(Path dirPath, String requestPath, String effectiveRoot) {
        HttpResponse response = new HttpResponse();
        try {
            StringBuilder html = new StringBuilder();
            html.append("<html><head><title>Index of ").append(requestPath).append("</title></head><body>\n");
            html.append("<h1>Index of ").append(requestPath).append("</h1>\n");
            html.append("<ul>\n");

            // Add parent directory link if not root
            if (!requestPath.equals("/")) {
                String parentPath = requestPath.substring(0, requestPath.lastIndexOf('/'));
                if (parentPath.isEmpty()) parentPath = "/";
                html.append("<li><a href=\"").append(parentPath).append("\">../</a></li>\n");
            }

            // List directory contents
            try (var stream = Files.list(dirPath)) {
                stream.sorted().forEach(path -> {
                    String fileName = path.getFileName().toString();
                    String filePath = requestPath.endsWith("/") ? requestPath + fileName : requestPath + "/" + fileName;
                    String displayName = Files.isDirectory(path) ? fileName + "/" : fileName;
                    html.append("<li><a href=\"").append(filePath).append("\">").append(displayName).append("</a></li>\n");
                });
            }

            html.append("</ul>\n");
            html.append("<hr><p>Server generated</p>\n");
            html.append("</body></html>\n");

            response.setStatus(HttpResponse.OK);
            response.setBody(html.toString().getBytes(StandardCharsets.UTF_8), "text/html");
        } catch (IOException e) {
            return errorResponse(HttpResponse.INTERNAL_SERVER_ERROR, "500");
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
    private HttpResponse handleDelete(String path, RouteMatch routeMatch) {
        String effectiveRoot = routeMatch.routeConfig.rootDirectory != null ? routeMatch.routeConfig.rootDirectory : wwwRoot;
        String relativePath = path.substring(routeMatch.path.length());
        if (relativePath.isEmpty()) relativePath = "/";
        Path filePath = Paths.get(effectiveRoot + relativePath);

        if (!filePath.toAbsolutePath().startsWith(Paths.get(effectiveRoot).toAbsolutePath())) {
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

    // ============ Route Extraction ============
    public static class RouteMatch {
        public final String path;
        public final ParsingHandler.RouteConfig routeConfig;

        public RouteMatch(String path, ParsingHandler.RouteConfig routeConfig) {
            this.path = path;
            this.routeConfig = routeConfig;
        }
    }

    private RouteMatch extractRoute(HttpRequest request) {
        String method = request.getMethod().toUpperCase();
        String requestPath = request.getPath();

        RouteMatch bestMatch = null;
        int longestMatch = -1;

        for (Map.Entry<String, ParsingHandler.RouteConfig> entry : config.routes.entrySet()) {
            String routePath = entry.getKey();

            if (!requestPath.startsWith(routePath))
                continue;

            if (routePath.length() > longestMatch) {
                bestMatch = new RouteMatch(routePath, entry.getValue());
                longestMatch = routePath.length();
            }
        }

        if (bestMatch == null) {
            throw new IllegalArgumentException("Route not found");
        }

        if (!bestMatch.routeConfig.methods.contains(method)) {
            throw new IllegalArgumentException("Method not allowed");
        }

        return bestMatch;
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