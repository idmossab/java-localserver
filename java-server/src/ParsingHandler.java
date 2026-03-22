import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public final class ParsingHandler {
    private final String jsonText;

    public ParsingHandler(String jsonText) {
        this.jsonText = jsonText;
    }

    public HashMap<String, Object> parse() {
        try {
            Object root = new JsonParser(jsonText).parseValueAndFinish();
            if (!(root instanceof HashMap)) {
                throw new IllegalArgumentException("config root must be object");
            }

            @SuppressWarnings("unchecked")
            HashMap<String, Object> config = (HashMap<String, Object>) root;

            checkString(config, "host");
            checkPorts(config.get("ports"));
            checkNumber(config, "default_server");
            checkNumber(config, "client_body_limit_bytes");
            checkStringMap(config.get("error_pages"), "error_pages");
            checkRoutes(config.get("routes"));

            Object cgi = config.get("cgi");
            if (cgi != null) {
                checkStringMap(cgi, "cgi");
            }

            return config;
        } catch (Exception e) {
            System.err.println("Parsing error: " + e.getMessage());
            System.exit(1);
            return null;
        }
    }

    private void checkString(HashMap<String, Object> object, String key) {
        if (!(object.get(key) instanceof String)) {
            throw new IllegalArgumentException(key + " must be string");
        }
    }

    private void checkNumber(HashMap<String, Object> object, String key) {
        if (!(object.get(key) instanceof Integer)) {
            throw new IllegalArgumentException(key + " must be number");
        }
    }

    private void checkPorts(Object value) {
        if (!(value instanceof ArrayList)) {
            throw new IllegalArgumentException("ports must be array");
        }

        ArrayList<?> ports = (ArrayList<?>) value;
        if (ports.isEmpty()) {
            throw new IllegalArgumentException("ports must not be empty");
        }

        for (Object port : ports) {
            if (!(port instanceof Integer)) {
                throw new IllegalArgumentException("ports must contain only numbers");
            }
        }
    }

    private void checkStringMap(Object value, String fieldName) {
        if (!(value instanceof HashMap)) {
            throw new IllegalArgumentException(fieldName + " must be object");
        }

        HashMap<?, ?> map = (HashMap<?, ?>) value;
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String) || !(entry.getValue() instanceof String)) {
                throw new IllegalArgumentException(fieldName + " must use string keys and string values");
            }
        }
    }

    private void checkRoutes(Object value) {
        if (!(value instanceof ArrayList)) {
            throw new IllegalArgumentException("routes must be array");
        }

        ArrayList<?> routes = (ArrayList<?>) value;
        for (Object route : routes) {
            if (!(route instanceof HashMap)) {
                throw new IllegalArgumentException("each route must be object");
            }

            @SuppressWarnings("unchecked")
            HashMap<String, Object> routeMap = (HashMap<String, Object>) route;

            checkString(routeMap, "path");
            checkString(routeMap, "root");
            checkString(routeMap, "index");

            Object methods = routeMap.get("methods");
            if (!(methods instanceof ArrayList)) {
                throw new IllegalArgumentException("route methods must be array");
            }
            for (Object method : (ArrayList<?>) methods) {
                if (!(method instanceof String)) {
                    throw new IllegalArgumentException("route methods must contain only strings");
                }
            }

            if (!(routeMap.get("autoindex") instanceof Boolean)) {
                throw new IllegalArgumentException("route autoindex must be boolean");
            }
        }
    }

    private static final class JsonParser {
        private final String text;
        private int index;

        private JsonParser(String text) {
            this.text = text;
        }

        public Object parseValueAndFinish() {
            skipWhitespace();
            Object value = parseValue();
            skipWhitespace();
            if (index != text.length()) {
                throw error("extra text after json");
            }
            return value;
        }

        private Object parseValue() {
            skipWhitespace();
            if (index >= text.length()) {
                throw error("unexpected end");
            }

            char current = text.charAt(index);
            if (current == '{') {
                return parseObject();
            }
            if (current == '[') {
                return parseArray();
            }
            if (current == '"') {
                return parseString();
            }
            if (current == 't' || current == 'f') {
                return parseBoolean();
            }
            if (current == 'n') {
                return parseNull();
            }
            if (current == '-' || Character.isDigit(current)) {
                return parseNumber();
            }
            throw error("invalid json value");
        }

        private HashMap<String, Object> parseObject() {
            HashMap<String, Object> object = new HashMap<>();
            expect('{');
            skipWhitespace();

            if (peek('}')) {
                index++;
                return object;
            }

            while (true) {
                String key = parseString();
                skipWhitespace();
                expect(':');
                Object value = parseValue();
                object.put(key, value);
                skipWhitespace();

                if (peek('}')) {
                    index++;
                    return object;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private ArrayList<Object> parseArray() {
            ArrayList<Object> array = new ArrayList<>();
            expect('[');
            skipWhitespace();

            if (peek(']')) {
                index++;
                return array;
            }

            while (true) {
                array.add(parseValue());
                skipWhitespace();

                if (peek(']')) {
                    index++;
                    return array;
                }
                expect(',');
                skipWhitespace();
            }
        }

        private String parseString() {
            expect('"');
            StringBuilder result = new StringBuilder();

            while (index < text.length()) {
                char current = text.charAt(index++);
                if (current == '"') {
                    return result.toString();
                }
                if (current == '\\') {
                    if (index >= text.length()) {
                        throw error("invalid escape");
                    }
                    char escaped = text.charAt(index++);
                    switch (escaped) {
                    case '"':
                    case '\\':
                    case '/':
                        result.append(escaped);
                        break;
                    case 'b':
                        result.append('\b');
                        break;
                    case 'f':
                        result.append('\f');
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 'r':
                        result.append('\r');
                        break;
                    case 't':
                        result.append('\t');
                        break;
                    case 'u':
                        result.append(readUnicode());
                        break;
                    default:
                        throw error("invalid escape");
                    }
                } else {
                    result.append(current);
                }
            }

            throw error("string not closed");
        }

        private char readUnicode() {
            if (index + 4 > text.length()) {
                throw error("invalid unicode escape");
            }
            String hex = text.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(hex, 16);
            } catch (NumberFormatException e) {
                throw error("invalid unicode escape");
            }
        }

        private Boolean parseBoolean() {
            if (text.startsWith("true", index)) {
                index += 4;
                return true;
            }
            if (text.startsWith("false", index)) {
                index += 5;
                return false;
            }
            throw error("invalid boolean");
        }

        private Object parseNull() {
            if (text.startsWith("null", index)) {
                index += 4;
                return null;
            }
            throw error("invalid null");
        }

        private Integer parseNumber() {
            int start = index;

            if (peek('-')) {
                index++;
            }
            if (index >= text.length() || !Character.isDigit(text.charAt(index))) {
                throw error("invalid number");
            }
            while (index < text.length() && Character.isDigit(text.charAt(index))) {
                index++;
            }
            if (peek('.')) {
                throw error("only integer numbers are allowed");
            }

            return Integer.parseInt(text.substring(start, index));
        }

        private void skipWhitespace() {
            while (index < text.length() && Character.isWhitespace(text.charAt(index))) {
                index++;
            }
        }

        private void expect(char expected) {
            if (!peek(expected)) {
                throw error("expected " + expected);
            }
            index++;
        }

        private boolean peek(char expected) {
            return index < text.length() && text.charAt(index) == expected;
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at position " + index);
        }
    }
}
