package com.apixa.contract.controller;

import com.apixa.contract.model.NormalizedSecurityContractDto;
import com.apixa.contract.service.ContractService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    public record ImportContentRequest(@NotBlank String content, String sourceName) {}
    public record ImportFileRequest(@NotBlank String path) {}

    private final ContractService contractService;

    public ContractController(ContractService contractService) { this.contractService = contractService; }

    @PostMapping("/import-content")
    public NormalizedSecurityContractDto importContent(@org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid ImportContentRequest request) {
        return contractService.importFromContent(
                request.sourceName() == null ? "openapi.yaml" : request.sourceName(), request.content());
    }

    @PostMapping("/import-file")
    public NormalizedSecurityContractDto importFile(@org.springframework.web.bind.annotation.RequestBody @jakarta.validation.Valid ImportFileRequest request) {
        return contractService.importFromFile(request.path());
    }

    @GetMapping
    public Map<String, NormalizedSecurityContractDto> list() { return contractService.all(); }

    @GetMapping("/{id}")
    public NormalizedSecurityContractDto get(@PathVariable String id) { return contractService.get(id); }
}
