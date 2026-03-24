package config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

final class JsonParser {
    private final String text;
    private int index;

    JsonParser(String text) {
        this.text = text;
    }

    public Object parseValueAndFinish() {
        skipWhitespace();
        Object value = parseValue();
        skipWhitespace();
        if (index != text.length()) {
            throw error("extra text after json");
        }
        return normalizeRootValue(value);
    }

    private Object normalizeRootValue(Object value) {
        if (!(value instanceof Map<?, ?>)) {
            return value;
        }

        Map<?, ?> root = (Map<?, ?>) value;
        Object servers = root.get("servers");
        if (!(servers instanceof List<?>)) {
            return value;
        }

        List<?> serverList = (List<?>) servers;
        if (serverList.isEmpty()) {
            throw new IllegalArgumentException("servers must not be empty");
        }

        Object firstServer = serverList.get(0);
        if (!(firstServer instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("each server must be object");
        }

        return firstServer;
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
