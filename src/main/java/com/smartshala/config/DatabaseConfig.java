package com.smartshala.config;

import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import javax.sql.DataSource;
import java.net.URI;
import java.net.URISyntaxException;

@Configuration
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String defaultUrl;

    @Value("${spring.datasource.username}")
    private String defaultUsername;

    @Value("${spring.datasource.password}")
    private String defaultPassword;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Bean
    public DataSource dataSource() {
        // 1. Check DATABASE_URL environment variable first (Render/Heroku/Railway default)
        String databaseUrl = System.getenv("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            // 2. Fall back to properties configured URL
            databaseUrl = defaultUrl;
        }

        if (databaseUrl != null && (databaseUrl.startsWith("postgres://") || databaseUrl.startsWith("postgresql://"))) {
            try {
                URI dbUri = new URI(databaseUrl);
                String username = defaultUsername;
                String password = defaultPassword;
                
                if (dbUri.getUserInfo() != null && dbUri.getUserInfo().contains(":")) {
                    username = dbUri.getUserInfo().split(":")[0];
                    password = dbUri.getUserInfo().split(":")[1];
                }
                
                String host = dbUri.getHost();
                int port = dbUri.getPort();
                String path = dbUri.getPath();
                
                String jdbcUrl = "jdbc:postgresql://" + host + (port != -1 ? ":" + port : "") + path;
                
                return DataSourceBuilder.create()
                        .url(jdbcUrl)
                        .username(username)
                        .password(password)
                        .driverClassName("org.postgresql.Driver")
                        .build();
            } catch (URISyntaxException | NullPointerException e) {
                System.err.println("Failed to parse database URL: " + e.getMessage() + ". Falling back to default settings.");
            }
        }

        // 3. Fallback to standard properties (in case it is already in jdbc:postgresql:// format)
        return DataSourceBuilder.create()
                .url(defaultUrl)
                .username(defaultUsername)
                .password(defaultPassword)
                .driverClassName(driverClassName)
                .build();
    }
}
