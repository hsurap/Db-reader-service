package com.example.dbreader.controller;

import com.example.dbreader.dto.AggregateRequest;
import com.example.dbreader.dto.FindRequest;
import com.example.dbreader.service.MongoQueryService;
import org.bson.Document;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/query/{db}/{collection}")
public class QueryController {

    private final MongoQueryService mongoQueryService;

    public QueryController(MongoQueryService mongoQueryService) {
        this.mongoQueryService = mongoQueryService;
    }

    @PostMapping("/find")
    public List<Document> find(@PathVariable String db, @PathVariable String collection,
                                @RequestBody(required = false) FindRequest request) {
        return mongoQueryService.find(db, collection, request != null ? request : new FindRequest());
    }

    @PostMapping("/aggregate")
    public List<Document> aggregate(@PathVariable String db, @PathVariable String collection,
                                     @RequestBody AggregateRequest request) {
        return mongoQueryService.aggregate(db, collection, request);
    }

    @PostMapping("/count")
    public Map<String, Long> count(@PathVariable String db, @PathVariable String collection,
                                    @RequestBody(required = false) FindRequest request) {
        Map<String, Object> filter = request != null ? request.getFilter() : null;
        long total = mongoQueryService.count(db, collection, filter);
        return Map.of("count", total);
    }
}
