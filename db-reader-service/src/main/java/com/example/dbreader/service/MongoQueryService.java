package com.example.dbreader.service;

import com.example.dbreader.dto.AggregateRequest;
import com.example.dbreader.dto.FindRequest;
import com.example.dbreader.exception.ReadOnlyViolationException;
import com.mongodb.client.AggregateIterable;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoCursor;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Every method here is read-only by construction: there is no insert/update/delete/drop
 * method anywhere in this class. Even so, aggregation pipelines are scanned for stages
 * that could write data or execute arbitrary server-side code, and system databases
 * are blocked outright.
 *
 * This is defense-in-depth on top of the real control: the Mongo user in mongodb.uri
 * should itself hold only a "read" role on the target databases.
 */
@Service
public class MongoQueryService {

    private final MongoClient mongoClient;

    @Value("${mongodb.default-max-limit:200}")
    private int defaultMaxLimit;

    @Value("${mongodb.hard-max-limit:1000}")
    private int hardMaxLimit;

    @Value("${mongodb.query-timeout-ms:15000}")
    private long queryTimeoutMs;

    private static final Set<String> BLOCKED_DATABASES = Set.of("admin", "local", "config");

    private static final Set<String> BLOCKED_AGGREGATION_STAGES = Set.of(
            "$out", "$merge", "$function", "$accumulator", "$where", "$currentOp", "$indexStats"
    );

    public MongoQueryService(MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public List<String> listDatabases() {
        List<String> names = new ArrayList<>();
        for (String name : mongoClient.listDatabaseNames()) {
            if (!BLOCKED_DATABASES.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }

    public List<String> listCollections(String dbName) {
        assertAllowedDatabase(dbName);
        List<String> names = new ArrayList<>();
        for (String name : mongoClient.getDatabase(dbName).listCollectionNames()) {
            names.add(name);
        }
        return names;
    }

    public List<Document> find(String dbName, String collectionName, FindRequest request) {
        assertAllowedDatabase(dbName);
        MongoCollection<Document> collection = mongoClient.getDatabase(dbName).getCollection(collectionName);

        Document filter = new Document(request.getFilter() == null ? Map.of() : request.getFilter());
        FindIterable<Document> iterable = collection.find(filter)
                .maxTime(queryTimeoutMs, TimeUnit.MILLISECONDS);

        if (request.getProjection() != null) {
            iterable = iterable.projection(new Document(request.getProjection()));
        }
        if (request.getSort() != null) {
            iterable = iterable.sort(new Document(request.getSort()));
        }

        iterable = iterable.limit(resolveLimit(request.getLimit()));

        if (request.getSkip() != null && request.getSkip() > 0) {
            iterable = iterable.skip(request.getSkip());
        }

        List<Document> results = new ArrayList<>();
        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        }
        return results;
    }

    public long count(String dbName, String collectionName, Map<String, Object> filter) {
        assertAllowedDatabase(dbName);
        MongoCollection<Document> collection = mongoClient.getDatabase(dbName).getCollection(collectionName);
        Document filterDoc = new Document(filter == null ? Map.of() : filter);
        return collection.countDocuments(filterDoc);
    }

    public List<Document> aggregate(String dbName, String collectionName, AggregateRequest request) {
        assertAllowedDatabase(dbName);
        if (request.getPipeline() == null || request.getPipeline().isEmpty()) {
            throw new IllegalArgumentException("pipeline must not be empty");
        }

        List<Bson> stages = new ArrayList<>();
        for (Map<String, Object> stage : request.getPipeline()) {
            for (String key : stage.keySet()) {
                if (BLOCKED_AGGREGATION_STAGES.contains(key)) {
                    throw new ReadOnlyViolationException("Aggregation stage not permitted: " + key);
                }
            }
            stages.add(new Document(stage));
        }
        stages.add(new Document("$limit", resolveLimit(request.getLimit())));

        MongoCollection<Document> collection = mongoClient.getDatabase(dbName).getCollection(collectionName);
        AggregateIterable<Document> iterable = collection.aggregate(stages)
                .maxTime(queryTimeoutMs, TimeUnit.MILLISECONDS)
                .allowDiskUse(false);

        List<Document> results = new ArrayList<>();
        try (MongoCursor<Document> cursor = iterable.iterator()) {
            while (cursor.hasNext()) {
                results.add(cursor.next());
            }
        }
        return results;
    }

    private int resolveLimit(Integer requested) {
        if (requested == null || requested <= 0) {
            return defaultMaxLimit;
        }
        return Math.min(requested, hardMaxLimit);
    }

    private void assertAllowedDatabase(String dbName) {
        if (BLOCKED_DATABASES.contains(dbName)) {
            throw new ReadOnlyViolationException("Access to database '" + dbName + "' is not permitted");
        }
    }
}
