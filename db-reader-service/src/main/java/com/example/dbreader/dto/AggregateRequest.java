package com.example.dbreader.dto;

import java.util.List;
import java.util.Map;

public class AggregateRequest {
    private List<Map<String, Object>> pipeline;
    private Integer limit;

    public List<Map<String, Object>> getPipeline() { return pipeline; }
    public void setPipeline(List<Map<String, Object>> pipeline) { this.pipeline = pipeline; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }
}
