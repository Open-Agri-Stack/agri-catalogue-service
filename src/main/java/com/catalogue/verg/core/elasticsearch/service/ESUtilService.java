package com.catalogue.verg.core.elasticsearch.service;

import co.elastic.clients.elasticsearch._types.Result;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.catalogue.verg.core.elasticsearch.dto.SearchCriteria;
import com.catalogue.verg.core.elasticsearch.dto.SearchResult;


import java.io.IOException;
import java.util.List;
import java.util.Map;

public interface ESUtilService {

    Result addDocument(String esIndexName, String type, String id,
                       Map<String, Object> document, String jsonFilePath) throws IOException;

    Result updateDocument(String index, String indexType, String entityId,
                          Map<String, Object> document, String jsonFilePath) throws IOException;

    void deleteDocument(String documentId, String esIndexName) throws IOException;

    void deleteDocumentsByCriteria(String esIndexName,
                                   co.elastic.clients.elasticsearch._types.query_dsl.Query query) throws IOException;

    SearchResult searchDocuments(String esIndexName, SearchCriteria searchCriteria) throws Exception;

    boolean isIndexPresent(String indexName) throws IOException;

    BulkResponse saveAll(String esIndexName, String type, List<JsonNode> entities) throws IOException;
}