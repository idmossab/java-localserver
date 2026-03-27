package utils;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class Session {
    private final String id;
    private final long createdAtMillis;
    private final Map<String, String> attributes = new LinkedHashMap<>();
    private long lastAccessedAtMillis;
    private long expiresAtMillis;

    public Session(String id, long createdAtMillis, long ttlMillis) {
        this.id = Objects.requireNonNull(id, "id");
        this.createdAtMillis = createdAtMillis;
        this.lastAccessedAtMillis = createdAtMillis;
        this.expiresAtMillis = createdAtMillis + ttlMillis;
    }

    public String getId() {
        return id;
    }

    public long getCreatedAtMillis() {
        return createdAtMillis;
    }

    public long getLastAccessedAtMillis() {
        return lastAccessedAtMillis;
    }

    public long getExpiresAtMillis() {
        return expiresAtMillis;
    }

    public boolean isExpired(long nowMillis) {
        return nowMillis >= expiresAtMillis;
    }

    public void touch(long nowMillis, long ttlMillis) {
        lastAccessedAtMillis = nowMillis;
        expiresAtMillis = nowMillis + ttlMillis;
    }

    public void putAttribute(String key, String value) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Session attribute key cannot be blank");
        }
        attributes.put(key, value);
    }

    public String getAttribute(String key) {
        return attributes.get(key);
    }

    public Map<String, String> getAttributes() {
        return Collections.unmodifiableMap(attributes);
    }

    public static final class Store {
        private static final int SESSION_ID_BYTES = 24;

        private final String cookieName;
        private final long ttlMillis;
        private final SecureRandom secureRandom = new SecureRandom();
        private final Map<String, Session> sessions = new LinkedHashMap<>();

        public Store(String cookieName, long ttlMillis) {
            if (cookieName == null || cookieName.isBlank()) {
                throw new IllegalArgumentException("Cookie name cannot be blank");
            }
            if (ttlMillis <= 0L) {
                throw new IllegalArgumentException("Session TTL must be positive");
            }
            this.cookieName = cookieName;
            this.ttlMillis = ttlMillis;
        }

        public String getCookieName() {
            return cookieName;
        }

        public Session resolve(Map<String, String> cookies, long nowMillis) {
            evictExpired(nowMillis);
            if (cookies == null || cookies.isEmpty()) {
                return null;
            }
            String sessionId = cookies.get(cookieName);
            if (sessionId == null || sessionId.isBlank()) {
                return null;
            }
            Session session = sessions.get(sessionId);
            if (session == null || session.isExpired(nowMillis)) {
                sessions.remove(sessionId);
                return null;
            }
            session.touch(nowMillis, ttlMillis);
            return session;
        }

        public Session create(long nowMillis) {
            String sessionId;
            do {
                sessionId = generateSessionId();
            } while (sessions.containsKey(sessionId));

            Session session = new Session(sessionId, nowMillis, ttlMillis);
            sessions.put(sessionId, session);
            return session;
        }

        public Session getOrCreate(Map<String, String> cookies, long nowMillis) {
            Session existing = resolve(cookies, nowMillis);
            if (existing != null) {
                return existing;
            }
            return create(nowMillis);
        }

        public Cookie buildSessionCookie(Session session, boolean secure) {
            Objects.requireNonNull(session, "session");
            long remainingSeconds = Math.max(0L, (session.getExpiresAtMillis() - Instant.now().toEpochMilli()) / 1000L);
            return Cookie.session(cookieName, session.getId(), remainingSeconds)
                    .secure(secure);
        }

        public int evictExpired(long nowMillis) {
            int removed = 0;
            Iterator<Map.Entry<String, Session>> iterator = sessions.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<String, Session> entry = iterator.next();
                if (entry.getValue().isExpired(nowMillis)) {
                    iterator.remove();
                    removed++;
                }
            }
            return removed;
        }

        public int size() {
            return sessions.size();
        }

        private String generateSessionId() {
            byte[] bytes = new byte[SESSION_ID_BYTES];
            secureRandom.nextBytes(bytes);
            StringBuilder builder = new StringBuilder(bytes.length * 2);
            for (byte value : bytes) {
                int unsigned = value & 0xFF;
                builder.append(Character.forDigit((unsigned >>> 4) & 0xF, 16));
                builder.append(Character.forDigit(unsigned & 0xF, 16));
            }
            return builder.toString();
        }
    }
}