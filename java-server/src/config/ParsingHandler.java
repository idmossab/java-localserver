package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ParsingHandler {
    private final String jsonText;
    public String host;
    public List<Integer> ports;

    public ParsingHandler(String jsonText) {
        this.jsonText = jsonText;
        this.ports = new ArrayList<>();
        parse();
    }

    public void parse() {
        try {
            Object root = new JsonParser(jsonText).parseValueAndFinish();
            if (!(root instanceof Map<?, ?>)) {
                throw new IllegalArgumentException("config root must be object");
            }

            Map<?, ?> config = (Map<?, ?>) root;
            host = readHost(config);
            ports = readPorts(config.get("ports"));
        } catch (Exception e) {
            System.err.println("Parsing error: " + e.getMessage());
            System.exit(1);
        }
    }

    private String readHost(Map<?, ?> config) {
        Object value = config.get("host");
        if (!(value instanceof String)) {
            throw new IllegalArgumentException("host must be string");
        }
        return (String) value;
    }

    private List<Integer> readPorts(Object value) {
        if (!(value instanceof List<?>)) {
            throw new IllegalArgumentException("ports must be array");
        }

        List<?> rawPorts = (List<?>) value;
        if (rawPorts.isEmpty()) {
            throw new IllegalArgumentException("ports must not be empty");
        }

        ArrayList<Integer> parsedPorts = new ArrayList<>();
        for (Object port : rawPorts) {
            if (!(port instanceof Integer)) {
                throw new IllegalArgumentException("ports must contain only numbers");
            }
            parsedPorts.add((Integer) port);
        }
        return parsedPorts;
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

        private Map<String, Object> parseObject() {
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

        private List<Object> parseArray() {
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
                        result.append(escaped);
                        break;
                    case 'n':
                        result.append('\n');
                        break;
                    case 't':
                        result.append('\t');
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

        // private char readUnicode() {
        //     if (index + 4 > text.length()) {
        //         throw error("invalid unicode escape");
        //     }
        //     String hex = text.substring(index, index + 4);
        //     index += 4;
        //     try {
        //         return (char) Integer.parseInt(hex, 16);
        //     } catch (NumberFormatException e) {
        //         throw error("invalid unicode escape");
        //     }
        // }

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
