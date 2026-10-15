package com.canteen.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.net.URI;

@Configuration
public class DatabaseConfig {

    @Value("${app.database-url}")
    private String databaseUrl;

    @Value("${spring.datasource.url:}")
    private String directSpringUrl;

    @Value("${spring.datasource.username:}")
    private String directSpringUsername;

    @Value("${spring.datasource.password:}")
    private String directSpringPassword;

    @Bean
    @Primary
    public DataSource dataSource() {
        if (directSpringUrl != null && !directSpringUrl.isBlank()) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(directSpringUrl);
            config.setUsername(directSpringUsername);
            config.setPassword(directSpringPassword);
            return new HikariDataSource(config);
        }

        String rawUrl = (databaseUrl != null && !databaseUrl.isBlank()) ? databaseUrl.trim() : "";
        if (rawUrl.startsWith("jdbc:")) {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(rawUrl);
            return new HikariDataSource(config);
        }

        try {
            String sanitized = rawUrl.replace("postgresql://", "postgres://");
            URI uri = new URI(sanitized);

            String userInfo = uri.getUserInfo();
            String username = "";
            String password = "";
            if (userInfo != null && userInfo.contains(":")) {
                String[] parts = userInfo.split(":", 2);
                username = parts[0];
                password = parts[1];
            } else if (userInfo != null) {
                username = userInfo;
            }

            String host = uri.getHost() != null ? uri.getHost() : "localhost";
            int port = uri.getPort() > 0 ? uri.getPort() : 5432;
            String path = uri.getPath() != null ? uri.getPath().replaceAll("^/", "") : "canteen_db";

            boolean isPostgres = rawUrl.contains("postgres");
            String jdbcUrl;
            if (isPostgres) {
                boolean isLocal = host.equals("localhost") || host.equals("127.0.0.1");
                String ssl = isLocal ? "sslmode=disable" : "sslmode=require";
                jdbcUrl = "jdbc:postgresql://" + host + ":" + port + "/" + path + "?" + ssl;
            } else {
                int mysqlPort = uri.getPort() > 0 ? uri.getPort() : 3306;
                jdbcUrl = "jdbc:mysql://" + host + ":" + mysqlPort + "/" + path + "?createDatabaseIfNotExist=true&useSSL=false";
            }

            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(jdbcUrl);
            config.setUsername(username);
            config.setPassword(password);
            config.setMaximumPoolSize(10);
            return new HikariDataSource(config);
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize DataSource from URL: " + rawUrl, e);
        }
    }
}
