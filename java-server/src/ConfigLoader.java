import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ConfigLoader {
    private static final long DEFAULT_CGI_TIMEOUT_MILLIS = 3_000L;
    private static final long DEFAULT_SESSION_TTL_MILLIS = Duration.ofMinutes(20).toMillis();
    private static final String DEFAULT_SESSION_COOKIE_NAME = "java_localserver_session";

    private final String path;
    private final Path configPath;
    private final Path projectRoot;
    private final Map<String, String> cgiInterpreters = new LinkedHashMap<>();
    private long cgiTimeoutMillis = DEFAULT_CGI_TIMEOUT_MILLIS;
    private long sessionTtlMillis = DEFAULT_SESSION_TTL_MILLIS;
    private String sessionCookieName = DEFAULT_SESSION_COOKIE_NAME;

    public ConfigLoader(String path) {
        this.path = Objects.requireNonNull(path, "path");
        this.configPath = Paths.get(path).toAbsolutePath().normalize();
        Path parent = this.configPath.getParent();
        this.projectRoot = parent == null ? Paths.get(".").toAbsolutePath().normalize() : parent;
    }

    public void load() {
        String json;
        try {
            json = Files.readString(configPath, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Unable to read config file: " + configPath, e);
        }

        cgiInterpreters.clear();
        cgiInterpreters.putAll(parseStringMapObject(findObject(json, "cgi")));

        Long timeoutValue = findLong(json, "cgi_timeout_millis");
        if (timeoutValue != null && timeoutValue.longValue() > 0L) {
            cgiTimeoutMillis = timeoutValue.longValue();
        }

        Long ttlSeconds = findLong(json, "session_ttl_seconds");
        if (ttlSeconds != null && ttlSeconds.longValue() > 0L) {
            sessionTtlMillis = Duration.ofSeconds(ttlSeconds.longValue()).toMillis();
        }

        String cookieName = findString(json, "session_cookie_name");
        if (cookieName != null && !cookieName.isBlank()) {
            sessionCookieName = cookieName;
        }
    }

    public String getPath() {
        return path;
    }

    public Path getConfigPath() {
        return configPath;
    }

    public Path getProjectRoot() {
        return projectRoot;
    }

    public Path resolveProjectPath(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            throw new IllegalArgumentException("Path cannot be blank");
        }
        Path rawPath = Paths.get(candidate);
        if (rawPath.isAbsolute()) {
            return rawPath.normalize();
        }
        return projectRoot.resolve(rawPath).normalize();
    }

    public Map<String, String> getCgiInterpreters() {
        return Collections.unmodifiableMap(cgiInterpreters);
    }

    public String getCgiInterpreter(String extension) {
        return cgiInterpreters.get(extension);
    }

    public void setCgiInterpreter(String extension, String interpreterPath) {
        validateExtension(extension);
        if (interpreterPath == null || interpreterPath.isBlank()) {
            throw new IllegalArgumentException("Interpreter path cannot be blank");
        }
        cgiInterpreters.put(extension, interpreterPath);
    }

    public long getCgiTimeoutMillis() {
        return cgiTimeoutMillis;
    }

    public void setCgiTimeoutMillis(long cgiTimeoutMillis) {
        if (cgiTimeoutMillis <= 0L) {
            throw new IllegalArgumentException("CGI timeout must be positive");
        }
        this.cgiTimeoutMillis = cgiTimeoutMillis;
    }

    public long getSessionTtlMillis() {
        return sessionTtlMillis;
    }

    public void setSessionTtlMillis(long sessionTtlMillis) {
        if (sessionTtlMillis <= 0L) {
            throw new IllegalArgumentException("Session TTL must be positive");
        }
        this.sessionTtlMillis = sessionTtlMillis;
    }

    public String getSessionCookieName() {
        return sessionCookieName;
    }

    public void setSessionCookieName(String sessionCookieName) {
        if (sessionCookieName == null || sessionCookieName.isBlank()) {
            throw new IllegalArgumentException("Session cookie name cannot be blank");
        }
        this.sessionCookieName = sessionCookieName;
    }

    private static void validateExtension(String extension) {
        if (extension == null || extension.isBlank() || extension.charAt(0) != '.') {
            throw new IllegalArgumentException("CGI extension must start with '.'");
        }
    }

    private static String findObject(String json, String key) {
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) {
            return "";
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int objectStart = nextNonWhitespace(json, colonIndex + 1);
        if (objectStart < 0 || json.charAt(objectStart) != '{') {
            return "";
        }
        int depth = 0;
        boolean inString = false;
        boolean escaping = false;
        for (int i = objectStart; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaping) {
                    escaping = false;
                } else if (ch == '\\') {
                    escaping = true;
                } else if (ch == '"') {
                    inString = false;
                }
                continue;
            }
            if (ch == '"') {
                inString = true;
                continue;
            }
            if (ch == '{') {
                depth++;
            } else if (ch == '}') {
                depth--;
                if (depth == 0) {
                    return json.substring(objectStart + 1, i);
                }
            }
        }
        throw new IllegalStateException("Unclosed object for key: " + key);
    }

    private static Map<String, String> parseStringMapObject(String objectBody) {
        Map<String, String> values = new LinkedHashMap<>();
        if (objectBody == null || objectBody.isBlank()) {
            return values;
        }
        int index = 0;
        while (index < objectBody.length()) {
            index = skipWhitespaceAndCommas(objectBody, index);
            if (index >= objectBody.length()) {
                break;
            }
            ParsedString key = readQuotedString(objectBody, index);
            index = skipWhitespaceAndCommas(objectBody, key.nextIndex);
            if (index >= objectBody.length() || objectBody.charAt(index) != ':') {
                throw new IllegalStateException("Expected ':' after key in object");
            }
            index = skipWhitespaceAndCommas(objectBody, index + 1);
            ParsedString value = readQuotedString(objectBody, index);
            values.put(key.value, value.value);
            index = value.nextIndex;
        }
        return values;
    }

    private static Long findLong(String json, String key) {
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int valueStart = nextNonWhitespace(json, colonIndex + 1);
        if (valueStart < 0) {
            return null;
        }
        int index = valueStart;
        while (index < json.length() && Character.isDigit(json.charAt(index))) {
            index++;
        }
        if (index == valueStart) {
            return null;
        }
        return Long.valueOf(json.substring(valueStart, index));
    }

    private static String findString(String json, String key) {
        int keyIndex = findKey(json, key);
        if (keyIndex < 0) {
            return null;
        }
        int colonIndex = json.indexOf(':', keyIndex);
        int valueStart = nextNonWhitespace(json, colonIndex + 1);
        if (valueStart < 0 || json.charAt(valueStart) != '"') {
            return null;
        }
        return readQuotedString(json, valueStart).value;
    }

    private static int findKey(String json, String key) {
        return json.indexOf('"' + key + '"');
    }

    private static int nextNonWhitespace(String value, int index) {
        int cursor = index;
        while (cursor >= 0 && cursor < value.length() && Character.isWhitespace(value.charAt(cursor))) {
            cursor++;
        }
        return cursor >= value.length() ? -1 : cursor;
    }

    private static int skipWhitespaceAndCommas(String value, int index) {
        int cursor = index;
        while (cursor < value.length()) {
            char current = value.charAt(cursor);
            if (!Character.isWhitespace(current) && current != ',') {
                break;
            }
            cursor++;
        }
        return cursor;
    }

    private static ParsedString readQuotedString(String value, int startIndex) {
        if (value.charAt(startIndex) != '"') {
            throw new IllegalStateException("Expected quoted string at index " + startIndex);
        }
        StringBuilder builder = new StringBuilder();
        boolean escaping = false;
        for (int index = startIndex + 1; index < value.length(); index++) {
            char current = value.charAt(index);
            if (escaping) {
                builder.append(current);
                escaping = false;
                continue;
            }
            if (current == '\\') {
                escaping = true;
                continue;
            }
            if (current == '"') {
                return new ParsedString(builder.toString(), index + 1);
            }
            builder.append(current);
        }
        throw new IllegalStateException("Unclosed quoted string");
    }

    private static final class ParsedString {
        private final String value;
        private final int nextIndex;

        private ParsedString(String value, int nextIndex) {
            this.value = value;
            this.nextIndex = nextIndex;
        }
    }
}
