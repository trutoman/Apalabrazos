package UE_Proyecto_Ingenieria.Apalabrazos.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class JwtConfig {
    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private static final String SECRET_ENV = "JWT_SECRET";
    private static final String ISSUER_ENV = "JWT_ISSUER";
    private static final String AUDIENCE_ENV = "JWT_AUDIENCE";
    private static final String EXP_MINUTES_ENV = "JWT_EXP_MINUTES";

    private static final String secret;
    private static final String issuer;
    private static final String audience;
    private static final long expMinutes;

    static {
        secret = readRequiredEnv(SECRET_ENV);
        issuer = readRequiredEnv(ISSUER_ENV);
        audience = readRequiredEnv(AUDIENCE_ENV);
        expMinutes = readLongEnv(EXP_MINUTES_ENV, 120L);
    }

    private JwtConfig() {
    }

    public static String getSecret() {
        return secret;
    }

    public static String getIssuer() {
        return issuer;
    }

    public static String getAudience() {
        return audience;
    }

    public static long getExpMinutes() {
        return expMinutes;
    }

    private static String readRequiredEnv(String key) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException("Missing required JWT env var: " + key);
        }
        return value.trim();
    }

    private static long readLongEnv(String key, long defaultValue) {
        String value = System.getenv(key);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException ex) {
            log.warn("Invalid value for {}: {}, using default {}", key, value, defaultValue);
            return defaultValue;
        }
    }
}
