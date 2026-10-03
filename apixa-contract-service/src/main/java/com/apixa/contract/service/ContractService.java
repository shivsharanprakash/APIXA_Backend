package com.apixa.contract.service;

import com.apixa.contract.model.ContractExtractionResultDto;
import com.apixa.contract.model.ImportedOpenApiDocument;
import com.apixa.contract.model.NormalizedSecurityContractDto;
import com.apixa.contract.model.OpenApiImportResultDto;
import com.apixa.contract.openapi.OpenApiContractParser;
import com.apixa.common.error.ApiException;
import com.apixa.common.model.SecurityPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Application service: parsing + in-memory contract store keyed by contract id. */
@Service
public class ContractService {

    private static final Logger log = LoggerFactory.getLogger(ContractService.class);
    private static final List<String> YAML_EXTENSIONS = List.of("yaml", "yml");
    private static final List<String> JSON_EXTENSIONS = List.of("json");
    private static final String FORMAT_YAML = "YAML";
    private static final String FORMAT_JSON = "JSON";
    private static final String DOCUMENT_URL = "/api/contracts/%s/document";

    private final OpenApiContractParser parser;
    private final Map<String, NormalizedSecurityContractDto> contracts = new ConcurrentHashMap<>();
    /**
     * Imported OpenAPI documents exactly as uploaded, kept in the same in-memory design as the
     * normalized contracts (this service has no entities/repositories; the configured SQLite
     * datasource is unused).
     */
    private final Map<String, ImportedOpenApiDocument> documents = new ConcurrentHashMap<>();


    public ContractService(OpenApiContractParser parser) { this.parser = parser; }

    public NormalizedSecurityContractDto importFromContent(String sourceName, String content) {
        NormalizedSecurityContractDto contract = parser.parse(sourceName, content);
        store(contract, sourceName, null, content);
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

    /**
     * Step 6: import a real OpenAPI YAML/JSON upload. The file must be non-empty, have a supported
     * file type and be a valid OpenAPI document (openapi, info, paths). The document is stored as-is
     * and import metadata is returned; no endpoint/security extraction is performed by this step.
     */
    public OpenApiImportResultDto importUpload(String fileName, String contentType, byte[] bytes) {
        if (fileName == null || fileName.isBlank()) {
            throw ApiException.badRequest("Uploaded file must have a name, e.g. openapi.yaml or openapi.json");
        }
        if (bytes == null || bytes.length == 0) {
            throw ApiException.badRequest("Uploaded file '" + fileName + "' is empty");
        }
        if (detectFormat(fileName, contentType) == null) {
            throw ApiException.badRequest("Unsupported OpenAPI file type '" + fileName + "' (content type "
                    + (contentType == null ? "unknown" : contentType)
                    + "); expected a .yaml, .yml or .json file");
        }
        String content = new String(bytes, StandardCharsets.UTF_8);
        NormalizedSecurityContractDto contract = parser.parseImported(fileName, content);
        ImportedOpenApiDocument document = store(contract, fileName, contentType, content);
        log.info("Imported OpenAPI document id={} file={} format={} openapiVersion={} sizeBytes={}",
                document.id(), document.fileName(), document.format(), document.openapiVersion(),
                document.sizeBytes());
        return toResult(document);
    }

    /** The imported document exactly as uploaded; used by the document retrieval endpoint. */
    public ImportedOpenApiDocument importedDocument(String id) {
        ImportedOpenApiDocument document = documents.get(id);
        if (document == null) {
            throw ApiException.notFound("Imported OpenAPI document " + id
                    + " not found (imports are kept in memory; re-import after restart)");
        }
        return document;
    }

    /**
     * Step 7: extract the contract information declared by the stored imported document. The
     * extraction is computed on demand from the imported content (no new storage), so it always
     * matches the document that was verified at import time.
     */
    public ContractExtractionResultDto extract(String id) {
        ImportedOpenApiDocument document = importedDocument(id);
        ContractExtractionResultDto result = parser.extract(id, document.fileName(), document.content());
        log.info("Extracted contract id={} file={} endpoints={} securitySchemes={}", result.contractId(),
                result.sourceFile(), result.endpointCount(), result.securitySchemeCount());
        return result;
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

    private ImportedOpenApiDocument store(NormalizedSecurityContractDto contract, String fileName,
                                          String contentType, String content) {
        contracts.put(contract.id(), contract);
        ImportedOpenApiDocument document = new ImportedOpenApiDocument(contract.id(), fileName, contentType,
                detectFormat(fileName, contentType), content.getBytes(StandardCharsets.UTF_8).length,
                contract.openapiVersion(), contract.title(), contract.version(), contract.openapiHash(),
                Instant.now().toString(), content);
        documents.put(document.id(), document);
        return document;
    }

    private OpenApiImportResultDto toResult(ImportedOpenApiDocument document) {
        return new OpenApiImportResultDto(document.id(), "IMPORTED", document.fileName(), document.format(),
                document.contentType(), document.sizeBytes(), document.openapiVersion(), document.title(),
                document.apiVersion(), document.openapiHash(), document.importedAt(), false,
                DOCUMENT_URL.formatted(document.id()));
    }

    /**
     * Detects YAML/JSON from the file name first, and from the content type when the name carries no
     * extension. Returns {@code null} when the file is not a supported OpenAPI file type.
     */
    private String detectFormat(String fileName, String contentType) {
        String extension = extension(fileName);
        if (extension != null && YAML_EXTENSIONS.contains(extension)) return FORMAT_YAML;
        if (extension != null && JSON_EXTENSIONS.contains(extension)) return FORMAT_JSON;
        if (extension != null && !extension.isEmpty()) return null;
        return formatFromContentType(contentType);
    }

    private String formatFromContentType(String contentType) {
        if (contentType == null) return null;
        String type = contentType.toLowerCase(Locale.ROOT).trim();
        if (type.startsWith("application/json") || type.endsWith("+json")) return FORMAT_JSON;
        if (type.startsWith("application/yaml") || type.startsWith("application/x-yaml")
                || type.startsWith("text/yaml") || type.startsWith("text/x-yaml")) return FORMAT_YAML;
        return null;
    }

    private String extension(String fileName) {
        if (fileName == null) return null;
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? null : fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
