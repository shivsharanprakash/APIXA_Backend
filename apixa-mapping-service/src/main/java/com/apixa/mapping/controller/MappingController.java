package com.apixa.mapping.controller;

import com.apixa.mapping.engine.EndpointMapper;
import com.apixa.mapping.model.MappingResultDto;
import com.apixa.mapping.service.MappingService;import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mappings")
public class MappingController {

    public record MapRequest(String contractId, String analysisId,
                             @jakarta.validation.Valid @jakarta.validation.constraints.NotEmpty
                             java.util.List<MappingService.MappingRequestEndpoint> contractEndpoints,
                             @jakarta.validation.Valid @jakarta.validation.constraints.NotEmpty
                             java.util.List<ImplEndpointDto> implementationEndpoints) {}

    public record ImplEndpointDto(String className, String methodName, String httpMethod, String path) {}

    private final MappingService service;

    public MappingController(MappingService service) { this.service = service; }

    @PostMapping("/map")
    public List<MappingResultDto> map(@Valid @RequestBody MapRequest request) {
        List<EndpointMapper.ImplEndpoint> impls = request.implementationEndpoints().stream()
                .map(e -> new EndpointMapper.ImplEndpoint(e.className(), e.methodName(), e.httpMethod(), e.path()))
                .toList();
        return service.map(request.contractId(), request.analysisId(),
                request.contractEndpoints(), impls);
    }

    @GetMapping
    public Map<String, List<MappingResultDto>> list() { return service.all(); }

    @GetMapping("/{id}")
    public List<MappingResultDto> get(@PathVariable String id) { return service.get(id); }
}
