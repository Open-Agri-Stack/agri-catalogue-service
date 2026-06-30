package com.catalogue.verg.core.elasticsearch.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.Refresh;
import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregate;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.*;
import co.elastic.clients.elasticsearch.core.*;
import co.elastic.clients.elasticsearch.core.bulk.BulkOperation;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import com.catalogue.verg.core.elasticsearch.config.EsConfig;
import com.catalogue.verg.core.elasticsearch.dto.FacetDTO;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.core.elasticsearch.dto.SearchResult;
import com.catalogue.verg.core.exception.CustomException;
import com.catalogue.verg.core.util.Constants;
import com.catalogue.verg.core.util.VergProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EsUtilServiceImpl implements ESUtilService {

    private final ElasticsearchClient elasticsearchClient;
    private final EsConfig esConfig;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private VergProperties vergServerProperties;

    @Autowired
    public EsUtilServiceImpl(ElasticsearchClient elasticsearchClient, EsConfig esConfig) {
        this.elasticsearchClient = elasticsearchClient;
        this.esConfig = esConfig;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    @Override
    public Result addDocument(String esIndexName, String type, String id,
                              Map<String, Object> document, String jsonFilePath) throws IOException {
        log.info("EsUtilServiceImpl :: addDocument");
        try {
            Map<String, Object> schema = loadSchema(jsonFilePath);
            document.keySet().retainAll(schema.keySet());

            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(esIndexName)
                    .id(id)
                    .document(document)
                    .refresh(Refresh.True));

            log.info("EsUtilServiceImpl :: addDocument: result {}", response.result());
            return response.result();
        } catch (Exception e) {
            log.error("Issue while indexing to ES: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public Result updateDocument(String index, String indexType, String entityId,
                                 Map<String, Object> updatedDocument, String jsonFilePath) throws IOException {
        try {
            Map<String, Object> schema = loadSchema(jsonFilePath);
            updatedDocument.keySet().retainAll(schema.keySet());

            IndexResponse response = elasticsearchClient.index(i -> i
                    .index(index)
                    .id(entityId)
                    .document(updatedDocument)
                    .refresh(Refresh.True));

            return response.result();
        } catch (IOException e) {
            log.error("Error updating document: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public void deleteDocument(String documentId, String esIndexName) {
        try {
            DeleteResponse response = elasticsearchClient.delete(d -> d
                    .index(esIndexName)
                    .id(documentId));

            if (response.result() == Result.Deleted) {
                log.info("Document deleted successfully from Elasticsearch.");
            } else {
                log.error("Document not found or failed to delete from Elasticsearch.");
            }
        } catch (Exception e) {
            log.error("Error deleting document from Elasticsearch.", e);
        }
    }

    @Override
    public void deleteDocumentsByCriteria(String esIndexName, Query query) throws IOException {
        try {
            SearchResponse<Map> searchResponse = elasticsearchClient.search(s -> s
                    .index(esIndexName)
                    .query(query), Map.class);

            List<Hit<Map>> hits = searchResponse.hits().hits();
            if (hits.isEmpty()) {
                log.info("No documents match the criteria.");
                return;
            }

            List<BulkOperation> deleteOps = hits.stream()
                    .map(hit -> BulkOperation.of(b -> b
                            .delete(d -> d.index(esIndexName).id(hit.id()))))
                    .collect(Collectors.toList());

            BulkResponse bulkResponse = elasticsearchClient.bulk(b -> b.operations(deleteOps));
            if (!bulkResponse.errors()) {
                log.info("Documents matching criteria deleted successfully.");
            } else {
                log.error("Some documents failed to delete from Elasticsearch.");
            }
        } catch (Exception e) {
            log.error("Error deleting documents by criteria from Elasticsearch.", e);
        }
    }

    @Override
    public SearchResult searchDocuments(String esIndexName, SearchCriteria searchCriteria) throws Exception {
        String searchString = searchCriteria.getSearchString();
        if (searchString != null
                && searchString.length() > vergServerProperties.getSearchStringMaxRegexLength()) {
            throw new RuntimeException("Search string exceeds maximum allowed length of "
                    + vergServerProperties.getSearchStringMaxRegexLength() + " characters.");
        }

        Query query = buildQuery(searchCriteria);
        int from = searchCriteria.getPageNumber() * searchCriteria.getPageSize();

        SearchRequest.Builder searchBuilder = new SearchRequest.Builder()
                .index(esIndexName)
                .query(query)
                .from(from)
                .aggregations(buildAggregations(searchCriteria.getFacets()));

        if (searchCriteria.getPageSize() != 0) {
            searchBuilder.size(searchCriteria.getPageSize());
        }
        if (searchCriteria.getRequestedFields() != null) {
            if (searchCriteria.getRequestedFields().isEmpty()) {
                log.error("Please specify at least one field to include in the results.");
            }
            List<String> fields = searchCriteria.getRequestedFields();
            searchBuilder.source(s -> s.filter(f -> f.includes(fields)));
        }

        addSort(searchCriteria, searchBuilder);

        SearchResponse<Map> response = elasticsearchClient.search(searchBuilder.build(), Map.class);

        List<Map<String, Object>> results = response.hits().hits().stream()
                .map(hit -> (Map<String, Object>) hit.source())
                .collect(Collectors.toList());

        SearchResult searchResult = new SearchResult();
        searchResult.setData(objectMapper.valueToTree(results));
        searchResult.setFacets((Map<String, List<?>>) (Map<?, ?>) extractFacetData(response, searchCriteria));
        searchResult.setTotalCount(response.hits().total().value());
        return searchResult;
    }

    @Override
    public boolean isIndexPresent(String indexName) throws IOException {
        try {
            return elasticsearchClient.indices().exists(e -> e.index(indexName)).value();
        } catch (IOException e) {
            log.error("Error checking if index exists", e);
            return false;
        }
    }

    @Override
    public BulkResponse saveAll(String esIndexName, String type,
                                List<JsonNode> entities) throws IOException {
        try {
            log.info("EsUtilServiceImpl :: saveAll");
            List<BulkOperation> operations = entities.stream().map(entity -> {
                String id = entity.get(Constants.ID).asText();
                Map<String, Object> entityMap = objectMapper.convertValue(entity, Map.class);
                return BulkOperation.of(b -> b
                        .index(i -> i.index(esIndexName).id(id).document(entityMap)));
            }).collect(Collectors.toList());

            return elasticsearchClient.bulk(b -> b.operations(operations));
        } catch (Exception e) {
            log.error(e.getMessage());
            throw new CustomException("error bulk uploading", e.getMessage(),
                    HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    // -------------------------------------------------------------------------
    // Query building
    // -------------------------------------------------------------------------

    private Query buildQuery(SearchCriteria searchCriteria) {
        BoolQuery.Builder boolBuilder = buildFilterQuery(searchCriteria.getFilterCriteriaMap());

        String searchString = searchCriteria.getSearchString();
        if (isNotBlank(searchString)) {
            String pattern = ".*" + searchString.toLowerCase() + ".*";
            boolBuilder.must(Query.of(q -> q.bool(b -> b
                    .should(Query.of(sq -> sq.regexp(r -> r
                            .field("searchTags.keyword")
                            .value(pattern)))))));
        }

        boolBuilder.must(buildQueryPart(searchCriteria.getQuery()));
        return Query.of(q -> q.bool(boolBuilder.build()));
    }

    private BoolQuery.Builder buildFilterQuery(Map<String, Object> filterCriteriaMap) {
        BoolQuery.Builder boolBuilder = new BoolQuery.Builder();
        List<Map<String, Object>> mustNotConditions = new ArrayList<>();

        if (filterCriteriaMap == null) return boolBuilder;

        filterCriteriaMap.forEach((field, value) -> {
            if (field.equals("must_not") && value instanceof List) {
                mustNotConditions.addAll((List<Map<String, Object>>) value);

            } else if (value instanceof Boolean) {
                boolean boolVal = (Boolean) value;
                boolBuilder.must(Query.of(q -> q.term(t -> t
                        .field(field).value(boolVal))));

            } else if (value instanceof List) {
                List<?> listVal = (List<?>) value;
                boolBuilder.must(Query.of(q -> q.terms(t -> t
                        .field(field + Constants.KEYWORD)
                        .terms(tv -> tv.value(toFieldValues(listVal))))));

            } else if (value instanceof String) {
                String strVal = (String) value;
                boolBuilder.must(Query.of(q -> q.terms(t -> t
                        .field(field + Constants.KEYWORD)
                        .terms(tv -> tv.value(List.of(FieldValue.of(strVal)))))));

            } else if (value instanceof Map) {
                Map<String, Object> nestedMap = (Map<String, Object>) value;
                if (isRangeQuery(nestedMap)) {
                    RangeQuery.Builder rangeBuilder = buildRangeQueryBuilder(field, nestedMap);
                    boolBuilder.must(Query.of(q -> q.bool(b -> b
                            .should(Query.of(sq -> sq.range(rangeBuilder.build())))
                            .should(Query.of(sq -> sq.bool(nb -> nb
                                    .mustNot(Query.of(eq -> eq.exists(e -> e.field(field))))))))));
                } else {
                    nestedMap.forEach((nestedField, nestedValue) -> {
                        String fullPath = field + "." + nestedField;
                        if (nestedValue instanceof Boolean) {
                            boolean bv = (Boolean) nestedValue;
                            boolBuilder.must(Query.of(q -> q.term(t -> t.field(fullPath).value(bv))));
                        } else if (nestedValue instanceof String) {
                            String sv = (String) nestedValue;
                            boolBuilder.must(Query.of(q -> q.term(t -> t
                                    .field(fullPath + Constants.KEYWORD).value(sv))));
                        } else if (nestedValue instanceof List) {
                            List<?> lv = (List<?>) nestedValue;
                            boolBuilder.must(Query.of(q -> q.terms(t -> t
                                    .field(fullPath + Constants.KEYWORD)
                                    .terms(tv -> tv.value(toFieldValues(lv))))));
                        }
                    });
                }
            }
        });

        mustNotConditions.forEach(condition -> boolBuilder.mustNot(buildQueryPart(condition)));
        return boolBuilder;
    }

    private Query buildQueryPart(Map<String, Object> queryMap) {
        if (queryMap == null || queryMap.isEmpty()) {
            return Query.of(q -> q.matchAll(m -> m));
        }
        for (Entry<String, Object> entry : queryMap.entrySet()) {
            Map<String, Object> value = (Map<String, Object>) entry.getValue();
            switch (entry.getKey()) {
                case Constants.BOOL:  return buildBoolQueryFromMap(value);
                case Constants.TERM:  return buildTermQueryFromMap(value);
                case Constants.TERMS: return buildTermsQueryFromMap(value);
                case Constants.MATCH: return buildMatchQueryFromMap(value);
                case Constants.RANGE: return buildRangeQueryFromMap(value);
                default: throw new IllegalArgumentException(Constants.UNSUPPORTED_QUERY + entry.getKey());
            }
        }
        return Query.of(q -> q.matchAll(m -> m));
    }

    private Query buildBoolQueryFromMap(Map<String, Object> boolMap) {
        BoolQuery.Builder builder = new BoolQuery.Builder();
        if (boolMap.containsKey(Constants.MUST))
            ((List<Map<String, Object>>) boolMap.get(Constants.MUST))
                    .forEach(m -> builder.must(buildQueryPart(m)));
        if (boolMap.containsKey(Constants.FILTER))
            ((List<Map<String, Object>>) boolMap.get(Constants.FILTER))
                    .forEach(f -> builder.filter(buildQueryPart(f)));
        if (boolMap.containsKey(Constants.MUST_NOT))
            ((List<Map<String, Object>>) boolMap.get(Constants.MUST_NOT))
                    .forEach(mn -> builder.mustNot(buildQueryPart(mn)));
        if (boolMap.containsKey(Constants.SHOULD))
            ((List<Map<String, Object>>) boolMap.get(Constants.SHOULD))
                    .forEach(s -> builder.should(buildQueryPart(s)));
        return Query.of(q -> q.bool(builder.build()));
    }

    private Query buildTermQueryFromMap(Map<String, Object> termMap) {
        for (Entry<String, Object> e : termMap.entrySet())
            return Query.of(q -> q.term(t -> t.field(e.getKey()).value(e.getValue().toString())));
        return null;
    }

    private Query buildTermsQueryFromMap(Map<String, Object> termsMap) {
        for (Entry<String, Object> e : termsMap.entrySet()) {
            List<?> values = (List<?>) e.getValue();
            return Query.of(q -> q.terms(t -> t
                    .field(e.getKey())
                    .terms(tv -> tv.value(toFieldValues(values)))));
        }
        return null;
    }

    private Query buildMatchQueryFromMap(Map<String, Object> matchMap) {
        for (Entry<String, Object> e : matchMap.entrySet())
            return Query.of(q -> q.match(m -> m
                    .field(e.getKey()).query(e.getValue().toString())));
        return null;
    }

    private Query buildRangeQueryFromMap(Map<String, Object> rangeMap) {
        for (Entry<String, Object> e : rangeMap.entrySet()) {
            RangeQuery.Builder b = buildRangeQueryBuilder(e.getKey(),
                    (Map<String, Object>) e.getValue());
            return Query.of(q -> q.range(b.build()));
        }
        return null;
    }

    private RangeQuery.Builder buildRangeQueryBuilder(String field, Map<String, Object> conditions) {
        RangeQuery.Builder builder = new RangeQuery.Builder().field(field);
        conditions.forEach((op, val) -> {
            switch (op) {
                case "gt":
                case Constants.SEARCH_OPERATION_GREATER_THAN:      builder.gt(JsonData.of(val));  break;
                case "gte":
                case Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS: builder.gte(JsonData.of(val)); break;
                case "lt":
                case Constants.SEARCH_OPERATION_LESS_THAN:         builder.lt(JsonData.of(val));  break;
                case "lte":
                case Constants.SEARCH_OPERATION_LESS_THAN_EQUALS:  builder.lte(JsonData.of(val)); break;
                default: throw new IllegalArgumentException(Constants.UNSUPPORTED_RANGE + op);
            }
        });
        return builder;
    }

    // -------------------------------------------------------------------------
    // Aggregations / facets
    // -------------------------------------------------------------------------

    private Map<String, Aggregation> buildAggregations(List<String> facets) {
        if (facets == null) return Collections.emptyMap();
        Map<String, Aggregation> aggs = new HashMap<>();
        facets.forEach(field -> aggs.put(field + "_agg",
                Aggregation.of(a -> a.terms(t -> t.field(field + ".keyword").size(250)))));
        return aggs;
    }

    private <T> Map<String, List<FacetDTO>> extractFacetData(
            SearchResponse<T> response, SearchCriteria searchCriteria) {
        Map<String, List<FacetDTO>> result = new HashMap<>();
        if (searchCriteria.getFacets() == null) return result;
        searchCriteria.getFacets().forEach(field -> {
            Aggregate agg = response.aggregations().get(field + "_agg");
            List<FacetDTO> facetList = agg.sterms().buckets().array().stream()
                    .filter(b -> !b.key().stringValue().isEmpty())
                    .map(b -> new FacetDTO(b.key().stringValue(), b.docCount()))
                    .collect(Collectors.toList());
            result.put(field, facetList);
        });
        return result;
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private Map<String, Object> loadSchema(String jsonFilePath) throws IOException {
        JsonSchemaFactory schemaFactory = JsonSchemaFactory.getInstance();
        InputStream schemaStream = schemaFactory.getClass().getResourceAsStream(jsonFilePath);
        return objectMapper.readValue(schemaStream, new TypeReference<Map<String, Object>>() {});
    }

    private void addSort(SearchCriteria searchCriteria, SearchRequest.Builder searchBuilder) {
        if (isNotBlank(searchCriteria.getOrderBy()) && isNotBlank(searchCriteria.getOrderDirection())) {
            SortOrder order = Constants.ASC.equals(searchCriteria.getOrderDirection())
                    ? SortOrder.Asc : SortOrder.Desc;
            String sortField = searchCriteria.getOrderBy() + Constants.KEYWORD;
            searchBuilder.sort(s -> s.field(f -> f.field(sortField).order(order)));
        }
    }

    private List<FieldValue> toFieldValues(List<?> values) {
        return values.stream()
                .map(v -> FieldValue.of(v.toString()))
                .collect(Collectors.toList());
    }

    private boolean isRangeQuery(Map<String, Object> map) {
        return map.keySet().stream().anyMatch(k ->
                k.equals(Constants.SEARCH_OPERATION_GREATER_THAN_EQUALS) ||
                        k.equals(Constants.SEARCH_OPERATION_LESS_THAN_EQUALS) ||
                        k.equals(Constants.SEARCH_OPERATION_GREATER_THAN) ||
                        k.equals(Constants.SEARCH_OPERATION_LESS_THAN));
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.trim().isEmpty();
    }
}