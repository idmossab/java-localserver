package utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Session {
    private final static Map<String, Map<String, String>> sessions = new HashMap<>();

    public static String create() {
        String sessionId = UUID.randomUUID().toString();
        sessions.put(sessionId, new HashMap<>());
        return sessionId;
    }

    public static void set(String sessionId, String key, String value) {
        Map<String, String> session = sessions.get(sessionId);
        if (session != null) {
            session.put(key, value);
        }
    }

    public static String get(String sessionId, String key) {
        Map<String, String> session = sessions.get(sessionId);
        if (session != null) {
            return session.get(key);
        }
        return null;
    }

    public static boolean exists(String sessionId) {
        return sessionId != null && sessions.containsKey(sessionId);
    }

    public static void destroy(String sessionId) {
        sessions.remove(sessionId);
    }
}