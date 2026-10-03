package com.apixa.mapping.controller;

import com.apixa.common.error.ApiException;
import com.apixa.mapping.engine.EndpointMapper;
import com.apixa.mapping.model.MappingResultDto;
import com.apixa.mapping.service.MappingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Step 10 endpoint mapping API.
 *
 * <p>The pre-existing route {@code POST /api/mappings/map} is reused — no duplicate mapping API was
 * added. It accepts the two already extracted datasets (Step 7 contract endpoints and Step 8 source
 * endpoints) and returns the deterministic correspondence. No OpenAPI document and no local source
 * path are read here: the mapping service never analyzes source itself and never calls the contract
 * or source analysis service.
 */
@RestController
@RequestMapping("/api/mappings")
public class MappingController {

    public record MapRequest(String contractId, String analysisId,
                             @jakarta.validation.Valid @NotEmpty
                             List<MappingService.MappingRequestEndpoint> contractEndpoints,
                             @jakarta.validation.Valid @NotEmpty
                             List<ImplEndpointDto> implementationEndpoints) {}

    /**
     * One Step 8 source endpoint. {@code file} and {@code lineStart} are the Step 8 source evidence;
     * they are optional so a caller may still supply only the four identity fields, and are carried
     * through unchanged into every mapping result.
     */
    public record ImplEndpointDto(String className, String methodName, String httpMethod, String path,
                                  String file, Integer lineStart) {}

    private final MappingService service;

    public MappingController(MappingService service) { this.service = service; }

    @PostMapping("/map")
    public List<MappingResultDto> map(@Valid @RequestBody MapRequest request) {
        if (request == null) {
            throw ApiException.badRequest("Mapping request body is required");
        }
        List<EndpointMapper.ImplEndpoint> impls = request.implementationEndpoints().stream()
                .map(e -> new EndpointMapper.ImplEndpoint(e.className(), e.methodName(), e.httpMethod(),
                        e.path(), e.file(), e.lineStart()))
                .toList();
        return service.map(request.contractId(), request.analysisId(),
                request.contractEndpoints(), impls);
    }

    @GetMapping
    public Map<String, List<MappingResultDto>> list() { return service.all(); }

    @GetMapping("/{id}")
    public List<MappingResultDto> get(@PathVariable String id) { return service.get(id); }
}
