package com.apixa.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "apixa.services")
public record GatewayServicesProperties(String projectService, String contractService, String sourceAnalysisService,
        String mappingService, String conformanceService, String evidenceService, String runtimeService,
        String impactService, String benchmarkService, String reportService) {

    public String url(String serviceName) {
        return switch (serviceName) {
            case "project-service" -> projectService;
            case "contract-service" -> contractService;
            case "source-analysis-service" -> sourceAnalysisService;
            case "mapping-service" -> mappingService;
            case "conformance-service" -> conformanceService;
            case "evidence-service" -> evidenceService;
            case "runtime-service" -> runtimeService;
            case "impact-service" -> impactService;
            case "benchmark-service" -> benchmarkService;
            case "report-service" -> reportService;
            default -> null;
        };
    }
}
