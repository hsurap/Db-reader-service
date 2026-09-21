package com.example.dbreader.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * You can configure the Mongo connection either way:
 *
 *  A) One full connection string:
 *       MONGO_URI=mongodb://user:pass@host1,host2,host3/?replicaSet=xyz&authSource=admin
 *
 *  B) Separate pieces, if that's how your credentials are handed to you:
 *       MONGO_HOST=host1,host2,host3
 *       MONGO_USERNAME=svc_user
 *       MONGO_PASSWORD=...
 *       MONGO_REPLICA_SET=xyz          (optional)
 *       MONGO_AUTH_SOURCE=admin        (optional, defaults to admin)
 *
 * If MONGO_URI is set, it wins and the separate fields are ignored.
 */
@Configuration
public class MongoConfig {

    @Value("${mongodb.uri:}")
    private String mongoUri;

    @Value("${mongodb.host:}")
    private String host;

    @Value("${mongodb.username:}")
    private String username;

    @Value("${mongodb.password:}")
    private String password;

    @Value("${mongodb.replica-set:}")
    private String replicaSet;

    @Value("${mongodb.auth-source:admin}")
    private String authSource;

    @Bean
    public MongoClient mongoClient() {
        String connectionString = resolveConnectionString();
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(connectionString))
                .applicationName("db-reader-service")
                .build();
        return MongoClients.create(settings);
    }

    private String resolveConnectionString() {
        if (mongoUri != null && !mongoUri.isBlank()) {
            return mongoUri;
        }

        if (host == null || host.isBlank()) {
            throw new IllegalStateException(
                    "No Mongo connection configured. Set MONGO_URI, or MONGO_HOST "
                            + "(+ MONGO_USERNAME / MONGO_PASSWORD).");
        }

        StringBuilder sb = new StringBuilder("mongodb://");
        if (username != null && !username.isBlank()) {
            sb.append(urlEncode(username));
            if (password != null && !password.isBlank()) {
                sb.append(':').append(urlEncode(password));
            }
            sb.append('@');
        }
        sb.append(host).append('/');

        StringBuilder params = new StringBuilder();
        appendParam(params, "authSource", authSource);
        appendParam(params, "replicaSet", replicaSet);
        appendParam(params, "readPreference", "secondaryPreferred");
        if (params.length() > 0) {
            sb.append('?').append(params);
        }
        return sb.toString();
    }

    private void appendParam(StringBuilder params, String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        if (params.length() > 0) {
            params.append('&');
        }
        params.append(key).append('=').append(value);
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
