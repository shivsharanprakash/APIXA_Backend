package com.apixa.contract.service;

import com.apixa.contract.model.NormalizedSecurityContractDto;
import com.apixa.contract.openapi.OpenApiContractParser;
import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Application service: parsing + in-memory contract store keyed by contract id. */
@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private final OpenApiContractParser parser;
    private final Map<String, NormalizedSecurityContractDto> contracts = new ConcurrentHashMap<>();

    public ContractService(OpenApiContractParser parser) { this.parser = parser; }

    public NormalizedSecurityContractDto importFromContent(String sourceName, String content) {
        NormalizedSecurityContractDto contract = parser.parse(sourceName, content);
        contracts.put(contract.id(), contract);
        log.info("Imported OpenAPI contract id={} title={} endpoints={}", contract.id(), contract.title(),
                contract.endpoints().size());
        return contract;
    }

    public NormalizedSecurityContractDto importFromFile(String path) {
        Path p = Path.of(path);
        if (!Files.isRegularFile(p)) {
            throw ApiException.badRequest("OpenAPI file not found: " + path);
        }
        try {
            return importFromContent(p.getFileName().toString(), Files.readString(p));
        } catch (IOException ex) {
            throw ApiException.internal("Cannot read OpenAPI file: " + path, ex);
        }
    }

    public NormalizedSecurityContractDto get(String id) {
        NormalizedSecurityContractDto c = contracts.get(id);
        if (c == null) throw ApiException.notFound("Contract " + id + " not found (contracts are kept in memory; re-import after restart)");
        return c;
    }

    public Map<String, NormalizedSecurityContractDto> all() { return new LinkedHashMap<>(contracts); }

    public SecurityPolicy expectedPolicy(String contractId, String method, String path) {
        NormalizedSecurityContractDto contract = get(contractId);
        return contract.endpoints().stream()
                .filter(e -> e.method().equalsIgnoreCase(method) && e.path().equals(path))
                .findFirst()
                .map(e -> e.expectedSecurity() == null ? SecurityPolicy.UNKNOWN : e.expectedSecurity())
                .orElseThrow(() -> ApiException.notFound("Endpoint " + method + " " + path + " not in contract " + contractId));
    }
}
