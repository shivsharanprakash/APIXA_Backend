package com.apixa.contract.controller;

import com.apixa.common.error.ApiException;
import com.apixa.contract.model.ContractExtractionResultDto;
import com.apixa.contract.model.ImportedOpenApiDocument;
import com.apixa.contract.model.NormalizedSecurityContractDto;
import com.apixa.contract.model.OpenApiImportResultDto;
import com.apixa.contract.service.ContractService;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/contracts")
public class ContractController {

    public record ImportContentRequest(@NotBlank String content, String sourceName) {}
    public record ImportFileRequest(@NotBlank String path) {}

    private final ContractService contractService;

    public ContractController(ContractService contractService) { this.contractService = contractService; }

    /** Step 6: multipart upload of a real OpenAPI YAML/JSON file (Postman form-data -> file). */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public OpenApiImportResultDto importUpload(@RequestParam(value = "file", required = false) MultipartFile file) {
        if (file == null) {
            throw ApiException.badRequest("Multipart field 'file' is required (form-data -> file)");
        }
        try {
            return contractService.importUpload(file.getOriginalFilename(), file.getContentType(), file.getBytes());
        } catch (IOException ex) {
            throw ApiException.internal("Cannot read the uploaded OpenAPI file", ex);
        }
    }

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

    /** Step 6 retrieval: the imported OpenAPI document exactly as uploaded (verifies storage). */
    @GetMapping("/{id}/document")
    public ResponseEntity<String> document(@PathVariable String id) {
        ImportedOpenApiDocument document = contractService.importedDocument(id);
        MediaType type = "JSON".equals(document.format()) ? MediaType.APPLICATION_JSON
                : MediaType.parseMediaType("application/yaml");
        return ResponseEntity.ok().contentType(type).body(document.content());
    }

    /**
     * Step 7 contract extraction: everything the imported document declares for the contract
     * (metadata, declared security, endpoints with operation info, parameters, request body,
     * responses). Read-only, computed on demand from the stored document.
     */
    @GetMapping("/{id}/extract")
    public ContractExtractionResultDto extract(@PathVariable String id) {
        return contractService.extract(id);
    }
}

