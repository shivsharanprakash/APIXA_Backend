package com.apixa.mapping.service;

import com.apixa.common.error.ApiException;
import com.apixa.mapping.engine.EndpointMapper;
import com.apixa.mapping.model.MappingResultDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Step 10 endpoint mapping: maps already extracted datasets onto each other.
 *
 * <p>Inputs are the Step 7 contract endpoints and the Step 8 source endpoints, supplied by the
 * caller. The service never reads a local source path, never analyzes Java and never calls the
 * contract or source analysis service, so the pipeline separation
 * (Step 7 / Step 8 → Step 10 → Step 11) is preserved.
 */
@Service
public class MappingService {

    private final EndpointMapper mapper;
    private final Map<String, List<MappingResultDto>> results = new ConcurrentHashMap<>();

    public MappingService(EndpointMapper mapper) { this.mapper = mapper; }

    public List<MappingResultDto> map(String contractId, String analysisId,
                                      List<MappingRequestEndpoint> contractEndpoints,
                                      List<EndpointMapper.ImplEndpoint> implEndpoints) {
        if (contractEndpoints == null || contractEndpoints.isEmpty()) {
            throw ApiException.badRequest("contractEndpoints is required and must not be empty");
        }
        if (implEndpoints == null || implEndpoints.isEmpty()) {
            throw ApiException.badRequest("implementationEndpoints is required and must not be empty");
        }
        // Deterministic output: the request order never leaks into the response.
        List<MappingRequestEndpoint> orderedContracts = contractEndpoints.stream()
                .sorted(Comparator.comparing(MappingRequestEndpoint::path, Comparator.nullsFirst(String::compareTo))
                        .thenComparing(MappingRequestEndpoint::method, Comparator.nullsFirst(String::compareTo)))
                .toList();

        List<MappingResultDto> out = new ArrayList<>();
        for (MappingRequestEndpoint ce : orderedContracts) {
            out.add(mapper.match(ce.method(), ce.path(), ce.operationId(), implEndpoints));
        }
        results.put(batchId(contractId, analysisId), List.copyOf(out));
        return out;
    }

    public Map<String, List<MappingResultDto>> all() { return new ConcurrentHashMap<>(results); }

    public List<MappingResultDto> get(String id) {
        List<MappingResultDto> r = results.get(id);
        if (r == null) throw ApiException.notFound("Mapping batch " + id + " not found");
        return r;
    }

    private String batchId(String contractId, String analysisId) {
        return (contractId == null ? "?" : contractId) + "-" + (analysisId == null ? "?" : analysisId);
    }

    public record MappingRequestEndpoint(String method, String path, String operationId) {}
}
