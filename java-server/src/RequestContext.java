import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import utils.Cookie;
import utils.Session;

public final class RequestContext {
    private final String method;
    private final String rawTarget;
    private final String requestPath;
    private final String queryString;
    private final String serverName;
    private final int serverPort;
    private final Map<String, String> requestHeaders;
    private final byte[] requestBody;
    private final HttpResponse response = new HttpResponse();
    private final List<String> notes = new ArrayList<>();
    private RouteMatch routeMatch;
    private Session session;
    private boolean newlyCreatedSession;

    public RequestContext(
            String method,
            String rawTarget,
            Map<String, String> requestHeaders,
            byte[] requestBody,
            String serverName,
            int serverPort) {
        this.method = normalizeMethod(method);
        this.rawTarget = rawTarget == null ? "/" : rawTarget;
        TargetParts targetParts = splitTarget(this.rawTarget);
        this.requestPath = targetParts.path;
        this.queryString = targetParts.queryString;
        this.requestHeaders = normalizeHeaders(requestHeaders);
        this.requestBody = requestBody == null ? new byte[0] : Arrays.copyOf(requestBody, requestBody.length);
        this.serverName = serverName == null || serverName.isBlank() ? "localhost" : serverName;
        this.serverPort = serverPort;
    }

    public String getMethod() {
        return method;
    }

    public String getRawTarget() {
        return rawTarget;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public String getServerName() {
        return serverName;
    }

    public int getServerPort() {
        return serverPort;
    }

    public byte[] getRequestBody() {
        return Arrays.copyOf(requestBody, requestBody.length);
    }

    public String getRequestBodyAsString() {
        return new String(requestBody, StandardCharsets.UTF_8);
    }

    public Map<String, String> getRequestHeaders() {
        return Collections.unmodifiableMap(requestHeaders);
    }

    public String getHeader(String name) {
        if (name == null) {
            return null;
        }
        return requestHeaders.get(name.toLowerCase(Locale.ROOT));
    }

    public RouteMatch getRouteMatch() {
        return routeMatch;
    }

    public RouteMatch requireRouteMatch() {
        if (routeMatch == null) {
            throw new IllegalStateException("Route metadata has not been attached to this request");
        }
        return routeMatch;
    }

    public void setRouteMatch(RouteMatch routeMatch) {
        this.routeMatch = Objects.requireNonNull(routeMatch, "routeMatch");
    }

    public Session getSession() {
        return session;
    }

    public void setSession(Session session, boolean newlyCreatedSession) {
        this.session = session;
        this.newlyCreatedSession = newlyCreatedSession;
    }

    public boolean hasNewlyCreatedSession() {
        return newlyCreatedSession;
    }

    public HttpResponse getResponse() {
        return response;
    }

    public void setResponseStatus(int statusCode) {
        response.setStatusCode(statusCode);
    }

    public void addResponseHeader(String name, String value) {
        response.addHeader(name, value);
    }

    public void addResponseCookie(Cookie cookie) {
        response.addHeader("Set-Cookie", cookie.toSetCookieHeader());
    }

    public void setResponseBody(byte[] body) {
        response.setBody(body);
    }

    public void setResponseBody(String body) {
        response.setBody(body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8));
    }

    public void addNote(String note) {
        if (note != null && !note.isBlank()) {
            notes.add(note);
        }
    }

    public List<String> getNotes() {
        return Collections.unmodifiableList(notes);
    }

    private static String normalizeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.toUpperCase(Locale.ROOT);
    }

    private static Map<String, String> normalizeHeaders(Map<String, String> requestHeaders) {
        Map<String, String> normalized = new LinkedHashMap<>();
        if (requestHeaders == null) {
            return normalized;
        }
        for (Map.Entry<String, String> entry : requestHeaders.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(entry.getKey().toLowerCase(Locale.ROOT), entry.getValue());
        }
        return normalized;
    }

    private static TargetParts splitTarget(String rawTarget) {
        int separator = rawTarget.indexOf('?');
        if (separator < 0) {
            return new TargetParts(rawTarget.isBlank() ? "/" : rawTarget, "");
        }
        String path = separator == 0 ? "/" : rawTarget.substring(0, separator);
        String query = separator == rawTarget.length() - 1 ? "" : rawTarget.substring(separator + 1);
        return new TargetParts(path, query);
    }

    public static final class HttpResponse {
        private int statusCode = 200;
        private final Map<String, List<String>> headers = new LinkedHashMap<>();
        private byte[] body = new byte[0];

        public int getStatusCode() {
            return statusCode;
        }

        public void setStatusCode(int statusCode) {
            this.statusCode = statusCode;
        }

        public Map<String, List<String>> getHeaders() {
            Map<String, List<String>> snapshot = new LinkedHashMap<>();
            for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
                snapshot.put(entry.getKey(), List.copyOf(entry.getValue()));
            }
            return Collections.unmodifiableMap(snapshot);
        }

        public void addHeader(String name, String value) {
            headers.computeIfAbsent(name, unused -> new ArrayList<>()).add(value);
        }

        public byte[] getBody() {
            return Arrays.copyOf(body, body.length);
        }

        public void setBody(byte[] body) {
            this.body = body == null ? new byte[0] : Arrays.copyOf(body, body.length);
        }
    }

    public static final class RouteMatch {
        private final String routePath;
        private final Path scriptPath;
        private final String cgiExtension;
        private final String scriptName;
        private final String pathInfo;
        private final Path pathInfoFilePath;
        private final Path workingDirectory;
        private final boolean cgiEnabled;
        private final boolean sessionEnabled;

        public RouteMatch(
                String routePath,
                Path scriptPath,
                String cgiExtension,
                String scriptName,
                String pathInfo,
                Path pathInfoFilePath,
                Path workingDirectory,
                boolean cgiEnabled,
                boolean sessionEnabled) {
            this.routePath = routePath == null ? "/" : routePath;
            this.scriptPath = scriptPath == null ? null : scriptPath.toAbsolutePath().normalize();
            this.cgiExtension = cgiExtension;
            this.scriptName = scriptName == null && scriptPath != null ? scriptPath.getFileName().toString() : scriptName;
            this.pathInfo = pathInfo == null ? "" : pathInfo;
            this.pathInfoFilePath = pathInfoFilePath == null ? null : pathInfoFilePath.toAbsolutePath().normalize();
            this.workingDirectory = workingDirectory == null && scriptPath != null
                    ? scriptPath.getParent()
                    : workingDirectory;
            this.cgiEnabled = cgiEnabled;
            this.sessionEnabled = sessionEnabled;
        }

        public String getRoutePath() {
            return routePath;
        }

        public Path getScriptPath() {
            return scriptPath;
        }

        public String getCgiExtension() {
            return cgiExtension;
        }

        public String getScriptName() {
            return scriptName;
        }

        public String getPathInfo() {
            return pathInfo;
        }

        public Path getPathInfoFilePath() {
            return pathInfoFilePath;
        }

        public Path getWorkingDirectory() {
            return workingDirectory;
        }

        public boolean isCgiEnabled() {
            return cgiEnabled;
        }

        public boolean isSessionEnabled() {
            return sessionEnabled;
        }
    }

    private static final class TargetParts {
        private final String path;
        private final String queryString;

        private TargetParts(String path, String queryString) {
            this.path = path;
            this.queryString = queryString;
        }
    }
}
