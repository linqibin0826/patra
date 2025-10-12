package com.patra.ingest.infra.rpc.pubmed;

import com.fasterxml.jackson.databind.JsonNode;
import com.patra.ingest.domain.exception.BatchPlanningException;
import com.patra.ingest.domain.port.PubmedSearchPort;
import com.patra.starter.provenance.common.exception.ProvenanceClientException;
import com.patra.starter.provenance.pubmed.PubMedClient;
import com.patra.starter.provenance.pubmed.model.request.ESearchRequest;
import com.patra.starter.provenance.pubmed.model.response.ESearchResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Infra adapter for {@link PubmedSearchPort} using {@link PubMedClient}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PubmedSearchPortImpl implements PubmedSearchPort {

    private final PubMedClient pubMedClient;

    @Override
    public int estimateCount(String term, JsonNode params) {
        try {
            ESearchRequest request = buildCountRequest(term, params);
            ESearchResponse response = pubMedClient.esearch(request);
            int count = response != null && response.result() != null ? response.result().count() : 0;
            log.info("[INGEST][INFRA] pubmed esearch count termHash={} count={}", safeHash(term), count);
            return Math.max(count, 0);
        } catch (ProvenanceClientException ex) {
            String msg = String.format("PubMed count lookup failed: %s", ex.getMessage());
            log.error("[INGEST][INFRA] {} termHash={}", msg, safeHash(term), ex);
            throw new BatchPlanningException(msg, ex);
        } catch (Exception ex) {
            String msg = String.format("PubMed count lookup unexpected error: %s", ex.getMessage());
            log.error("[INGEST][INFRA] {} termHash={}", msg, safeHash(term), ex);
            throw new BatchPlanningException(msg, ex);
        }
    }

    private static String safeHash(String s) {
        if (s == null) return "null";
        int h = s.hashCode();
        return Integer.toHexString(h);
    }

    private static ESearchRequest buildCountRequest(String term, JsonNode params) {
        // Pull a few relevant filters from compiled params (if present)
        String sort = getText(params, "sort");
        String datetype = getText(params, "datetype");
        String mindate = getText(params, "mindate");
        String maxdate = getText(params, "maxdate");
        String field = getText(params, "field");
        String reldate = getText(params, "reldate");

        // Build a count-only ESearchRequest (retmode=json, rettype=count)
        return new ESearchRequest(
            "pubmed",
            term,
            null, // retstart
            null, // retmax
            "json",
            "count",
            sort,
            datetype,
            mindate,
            maxdate,
            field,
            reldate,
            null, // usehistory
            null, // webenv
            null, // queryKey
            null, // apiKey
            null, // tool
            null  // email
        );
    }

    private static String getText(JsonNode node, String field) {
        if (node == null || node.isNull()) return null;
        JsonNode v = node.get(field);
        return (v != null && !v.isNull() && v.isTextual()) ? v.asText() : null;
    }
}

