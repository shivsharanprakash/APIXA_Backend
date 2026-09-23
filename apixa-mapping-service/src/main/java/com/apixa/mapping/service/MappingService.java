package com.apixa.mapping.service;

import com.apixa.common.error.ApiException;
import com.apixa.mapping.engine.EndpointMapper;
import com.apixa.mapping.model.MappingResultDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MappingService {

    private final EndpointMapper mapper;
    private final Map<String, List<MappingResultDto>> results = new ConcurrentHashMap<>();

    public MappingService(EndpointMapper mapper) { this.mapper = mapper; }

    public List<MappingResultDto> map(String contractId, String analysisId,
                                      List<MappingRequestEndpoint> contractEndpoints,
                                      List<EndpointMapper.ImplEndpoint> implEndpoints) {
        List<MappingResultDto> out = new ArrayList<>();
        for (MappingRequestEndpoint ce : contractEndpoints) {
            out.add(mapper.match(ce.method(), ce.path(), ce.operationId(), implEndpoints));
        }
        results.put(batchId(contractId, analysisId), out);
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
