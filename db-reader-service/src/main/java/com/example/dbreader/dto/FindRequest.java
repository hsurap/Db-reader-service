package com.example.dbreader.dto;

import java.util.Map;

public class FindRequest {
    private Map<String, Object> filter = Map.of();
    private Map<String, Object> projection;
    private Map<String, Object> sort;
    private Integer limit;
    private Integer skip;

    public Map<String, Object> getFilter() { return filter; }
    public void setFilter(Map<String, Object> filter) { this.filter = filter; }

    public Map<String, Object> getProjection() { return projection; }
    public void setProjection(Map<String, Object> projection) { this.projection = projection; }

    public Map<String, Object> getSort() { return sort; }
    public void setSort(Map<String, Object> sort) { this.sort = sort; }

    public Integer getLimit() { return limit; }
    public void setLimit(Integer limit) { this.limit = limit; }

    public Integer getSkip() { return skip; }
    public void setSkip(Integer skip) { this.skip = skip; }
}
