package com.eduplay.backend.infrastructure;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

/**
 * Derives {@code spring.datasource.url} (and credentials when embedded) from the deployment
 * environment variables, so the same image works with local PostgreSQL and Supabase:
 * <ol>
 *   <li>An explicit {@code spring.datasource.url} / {@code SPRING_DATASOURCE_URL} always wins.</li>
 *   <li>{@code DATABASE_URL} in JDBC form ({@code jdbc:postgresql://...}) is used as-is.</li>
 *   <li>{@code DATABASE_URL} in libpq/Supabase form ({@code postgresql://user:pass@host:port/db?...})
 *       is converted to JDBC; embedded user and password are used unless DB_USERNAME/DB_PASSWORD are set.</li>
 *   <li>Otherwise the URL is built from DB_HOST, DB_PORT, DB_NAME and DB_SSL_MODE.</li>
 * </ol>
 */
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "eduplayDatabaseUrl";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("spring.datasource.url"))) {
            return;
        }
        Map<String, Object> properties = resolve(
                environment.getProperty("DATABASE_URL"),
                environment.getProperty("DB_HOST", "localhost"),
                environment.getProperty("DB_PORT", "5432"),
                environment.getProperty("DB_NAME", "eduplay"),
                environment.getProperty("DB_SSL_MODE", "prefer"),
                StringUtils.hasText(environment.getProperty("DB_USERNAME")),
                StringUtils.hasText(environment.getProperty("DB_PASSWORD")));
        environment.getPropertySources().addFirst(new MapPropertySource(PROPERTY_SOURCE_NAME, properties));
    }

    static Map<String, Object> resolve(String databaseUrl, String host, String port, String name, String sslMode,
                                       boolean usernameProvided, boolean passwordProvided) {
        Map<String, Object> properties = new HashMap<>();
        if (!StringUtils.hasText(databaseUrl)) {
            properties.put("spring.datasource.url",
                    "jdbc:postgresql://%s:%s/%s?sslmode=%s".formatted(host, port, name, sslMode));
            return properties;
        }
        String url = databaseUrl.trim();
        if (url.startsWith("jdbc:")) {
            properties.put("spring.datasource.url", url);
            return properties;
        }
        if (!url.startsWith("postgres://") && !url.startsWith("postgresql://")) {
            throw new IllegalStateException("DATABASE_URL must start with jdbc:postgresql://, postgresql:// or postgres://");
        }
        URI uri = URI.create(url.replaceFirst("^postgres(ql)?://", "postgresql://"));
        String query = uri.getRawQuery();
        if (query == null || !query.contains("sslmode=")) {
            query = (query == null ? "" : query + "&") + "sslmode=" + sslMode;
        }
        int uriPort = uri.getPort() == -1 ? 5432 : uri.getPort();
        properties.put("spring.datasource.url",
                "jdbc:postgresql://%s:%d%s?%s".formatted(uri.getHost(), uriPort, uri.getRawPath(), query));

        String userInfo = uri.getRawUserInfo();
        if (userInfo != null) {
            String[] parts = userInfo.split(":", 2);
            if (!usernameProvided) {
                properties.put("spring.datasource.username", decode(parts[0]));
            }
            if (parts.length == 2 && !passwordProvided) {
                properties.put("spring.datasource.password", decode(parts[1]));
            }
        }
        return properties;
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }
}
