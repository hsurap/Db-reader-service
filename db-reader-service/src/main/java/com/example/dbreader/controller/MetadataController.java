package com.example.dbreader.controller;

import com.example.dbreader.service.MongoQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/metadata")
public class MetadataController {

    private final MongoQueryService mongoQueryService;

    public MetadataController(MongoQueryService mongoQueryService) {
        this.mongoQueryService = mongoQueryService;
    }

    @GetMapping("/databases")
    public List<String> listDatabases() {
        return mongoQueryService.listDatabases();
    }

    @GetMapping("/databases/{db}/collections")
    public List<String> listCollections(@PathVariable String db) {
        return mongoQueryService.listCollections(db);
    }
}
