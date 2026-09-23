package com.apixa.sourceanalysis.engine;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.sourceanalysis.model.SecurityEvidenceDto;
import com.apixa.sourceanalysis.model.SourceAnalysisResultDto;
import com.apixa.sourceanalysis.model.SourceEndpointDto;
import org.springframework.stereotype.Component;
import spoon.Launcher;
import spoon.reflect.declaration.CtAnnotation;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.visitor.filter.TypeFilter;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Static analysis engine for local Spring Boot projects.
 * Extracts endpoint mappings and Spring Security rules with file/line evidence.
 * Independently testable: no HTTP, no persistence.
 */
@Component
public class SpringSourceAnalyzer {

    public static final String ANALYZER_VERSION = "spoon-1.0.0";

    private static final Pattern REQUEST_MATCHERS = Pattern.compile("requestMatchers\\s*\\(([^\\]]*?)\\)");
    private static final Pattern HAS_ROLE = Pattern.compile("hasRole\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern HAS_ANY_ROLE = Pattern.compile("hasAnyRole\\s*\\(([^)]*)\\)");
    private static final Pattern HAS_AUTHORITY = Pattern.compile("hasAuthority\\s*\\(\\s*\"([^\"]+)\"\\s*\\)");
    private static final Pattern HAS_ANY_AUTHORITY = Pattern.compile("hasAnyAuthority\\s*\\(([^)]*)\\)");
    private static final Pattern PERMIT_ALL = Pattern.compile("\\.permitAll\\s*\\(\\s*\\)");
    private static final Pattern AUTHENTICATED = Pattern.compile("\\.authenticated\\s*\\(\\s*\\)");
    private static final Pattern STRING_LITERALS = Pattern.compile("\"([^\"]+)\"");
    private static final Pattern ANY_REQUEST = Pattern.compile("anyRequest\\s*\\(\\s*\\)");
    private static final Pattern PRE_AUTHORIZE_ROLE = Pattern.compile("hasRole\\s*\\(\\s*'?\"?([A-Z_0-9]+)'?\"?\\s*\\)");
    private static final Pattern PRE_AUTHORIZE_AUTH = Pattern.compile("hasAuthority\\s*\\(\\s*'?\"?([A-Za-z_0-9:]+)'?\"?\\s*\\)");
    private static final Pattern PRE_AUTHORIZE_PERMIT = Pattern.compile("permitAll");

    public SourceAnalysisResultDto analyze(String projectPath) {
        List<Path> javaFiles = collectJavaFiles(projectPath);
        List<SourceEndpointDto> endpoints = new ArrayList<>();
        List<SecurityEvidenceDto> rules = new ArrayList<>();

        for (Path file : javaFiles) {
            analyzeFile(file, endpoints, rules);
        }
        endpoints.sort(Comparator.comparing(SourceEndpointDto::path).thenComparing(SourceEndpointDto::httpMethod));
        return new SourceAnalysisResultDto(projectPath, ANALYZER_VERSION, javaFiles.size(), endpoints, rules);
    }

    private void analyzeFile(Path file, List<SourceEndpointDto> endpoints, List<SecurityEvidenceDto> rules) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return; // unreadable file: skip silently, evidence would be unavailable anyway
        }
        extractSecurityFilterRules(file.toString(), content, rules);

        Launcher launcher = new Launcher();
        launcher.addInputResource(file.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        launcher.buildModel();

        for (CtClass<?> ctClass : launcher.getModel().getElements(new TypeFilter<>(CtClass.class))) {
            boolean isController = ctClass.getAnnotations().stream().anyMatch(a -> {
                String n = a.getAnnotationType().getSimpleName();
                return n.equals("RestController") || n.equals("Controller");
            });
            if (isController) {
                String basePath = requestMappingPath(ctClass);
                extractEndpoints(ctClass, basePath, file.toString(), content, endpoints, rules);
            }
        }
    }

    private void extractEndpoints(CtClass<?> ctClass, String basePath, String fileName, String content,
                                  List<SourceEndpointDto> endpoints, List<SecurityEvidenceDto> rules) {
        for (Object o : ctClass.getMethods()) {
            CtMethod<?> method = (CtMethod<?>) o;
            String httpMethod = null;
            String path = null;
            for (CtAnnotation<?> a : method.getAnnotations()) {
                String n = a.getAnnotationType().getSimpleName();
                switch (n) {
                    case "GetMapping" -> { httpMethod = "GET"; path = pathOf(a, "/"); }
                    case "PostMapping" -> { httpMethod = "POST"; path = pathOf(a, "/"); }
                    case "PutMapping" -> { httpMethod = "PUT"; path = pathOf(a, "/"); }
                    case "DeleteMapping" -> { httpMethod = "DELETE"; path = pathOf(a, "/"); }
                    case "PatchMapping" -> { httpMethod = "PATCH"; path = pathOf(a, "/"); }
                    case "RequestMapping" -> {
                        String v = pathOf(a, null);
                        if (v != null) { path = v; httpMethod = "ANY"; }
                    }
                    default -> { }
                }
            }
            if (httpMethod == null) continue;
            String fullPath = joinPath(basePath, path);
            List<SecurityEvidenceDto> evidence = new ArrayList<>();
            SecurityPolicy policy = methodSecurity(method, fileName, evidence);
            if (policy == null) {
                policy = configPolicyFor(fullPath, rules, evidence);
            }
            endpoints.add(new SourceEndpointDto(ctClass.getSimpleName(), method.getSimpleName(), httpMethod,
                    fullPath, fileName, method.getPosition() != null ? method.getPosition().getLine() : null,
                    policy == null ? SecurityPolicy.UNKNOWN : policy, evidence));
        }
    }

    private SecurityPolicy methodSecurity(CtMethod<?> method, String fileName, List<SecurityEvidenceDto> evidence) {
        for (CtAnnotation<?> a : method.getAnnotations()) {
            String n = a.getAnnotationType().getSimpleName();
            String value = a.getValueAsString("value");
            int line = a.getPosition() != null ? a.getPosition().getLine() : method.getPosition().getLine();
            switch (n) {
                case "PreAuthorize" -> {
                    if (value != null) {
                        List<String> roles = new ArrayList<>();
                        List<String> authorities = new ArrayList<>();
                        Matcher m = PRE_AUTHORIZE_ROLE.matcher(value);
                        while (m.find()) roles.add(m.group(1).startsWith("ROLE_") ? m.group(1).substring(5) : m.group(1));
                        m = PRE_AUTHORIZE_AUTH.matcher(value);
                        while (m.find()) authorities.add(m.group(1));
                        if (PRE_AUTHORIZE_PERMIT.matcher(value).find()) {
                            evidence.add(new SecurityEvidenceDto(fileName, line, line, "PERMIT_ALL", null, null, null, null, value));
                            return SecurityPolicy.permitAll();
                        }
                        if (!roles.isEmpty()) {
                            evidence.add(new SecurityEvidenceDto(fileName, line, line, "ROLE_CHECK", null, roles, null, null, value));
                            return SecurityPolicy.roles("AUTHENTICATED", roles);
                        }
                        if (!authorities.isEmpty()) {
                            evidence.add(new SecurityEvidenceDto(fileName, line, line, "AUTHORITY_CHECK", null, null, authorities, null, value));
                            return SecurityPolicy.authorities("AUTHENTICATED", authorities);
                        }
                        evidence.add(new SecurityEvidenceDto(fileName, line, line, "AUTHENTICATED", null, null, null, null, value));
                        return SecurityPolicy.authenticated();
                    }
                }
                case "Secured", "RolesAllowed" -> {
                    if (value != null && !value.isBlank()) {
                        List<String> roles = new ArrayList<>();
                        for (String role : value.replace("[", "").replace("]", "").split(",")) {
                            String r = role.trim().replace("\"", "").replace("'", "");
                            if (!r.isEmpty()) roles.add(r.startsWith("ROLE_") ? r.substring(5) : r);
                        }
                        evidence.add(new SecurityEvidenceDto(fileName, line, line, "ROLE_CHECK", null, roles, null, null, n + ": " + value));
                        return SecurityPolicy.roles("AUTHENTICATED", roles);
                    }
                }
                default -> { }
            }
        }
        return null;
    }

    /** Map a config-level rule (from requestMatchers scan) onto a concrete endpoint path. */
    private SecurityPolicy configPolicyFor(String fullPath, List<SecurityEvidenceDto> rules,
                                           List<SecurityEvidenceDto> evidence) {
        org.springframework.util.AntPathMatcher matcher = new org.springframework.util.AntPathMatcher();
        for (SecurityEvidenceDto rule : rules) {
            if (rule.pathPattern() != null && matcher.match(rule.pathPattern(), fullPath)) {
                evidence.add(rule);
                if ("PERMIT_ALL".equals(rule.ruleType())) return SecurityPolicy.permitAll();
                if ("AUTHENTICATED".equals(rule.ruleType())) return SecurityPolicy.authenticated();
                if (rule.roles() != null && !rule.roles().isEmpty()) return SecurityPolicy.roles("AUTHENTICATED", rule.roles());
                if (rule.authorities() != null && !rule.authorities().isEmpty()) return SecurityPolicy.authorities("AUTHENTICATED", rule.authorities());
            }
        }
        return null;
    }

    /** Regex scan of SecurityFilterChain-style configuration with line numbers. */
    void extractSecurityFilterRules(String fileName, String content, List<SecurityEvidenceDto> rules) {
        Matcher anyMatcher = ANY_REQUEST.matcher(content);
        while (anyMatcher.find()) {
            int line = lineNumberAt(content, anyMatcher.start());
            String window = content.substring(anyMatcher.start(), Math.min(content.length(), anyMatcher.start() + 80));
            if (AUTHENTICATED.matcher(window).find()) {
                rules.add(new SecurityEvidenceDto(fileName, line, line, "AUTHENTICATED", "**", null, null, null,
                        snippet(content, anyMatcher.start(), 60)));
            } else if (PERMIT_ALL.matcher(window).find()) {
                rules.add(new SecurityEvidenceDto(fileName, line, line, "PERMIT_ALL", "**", null, null, null,
                        snippet(content, anyMatcher.start(), 60)));
            }
        }

        Matcher rm = REQUEST_MATCHERS.matcher(content);
        while (rm.find()) {
            int line = lineNumberAt(content, rm.start());
            List<String> patterns = new ArrayList<>();
            Matcher literals = STRING_LITERALS.matcher(rm.group(1));
            while (literals.find()) patterns.add(literals.group(1));
            // scan the chained calls that follow (up to the next requestMatchers / anyRequest / statement end)
            int windowEnd = content.length();
            Matcher nextRm = REQUEST_MATCHERS.matcher(content).region(rm.end(), content.length());
            Matcher nextAny = ANY_REQUEST.matcher(content).region(rm.end(), content.length());
            if (nextRm.find()) windowEnd = Math.min(windowEnd, nextRm.start() + rm.end() - rm.start() + rm.start());
            if (nextAny.find()) windowEnd = Math.min(windowEnd, nextAny.end());
            String window = content.substring(rm.start(), windowEnd);
            // stop at the first semicolon (statement end) if present
            int semi = window.indexOf(';');
            if (semi > 0) window = window.substring(0, semi);

            String ruleType = null;
            List<String> roles = null;
            List<String> authorities = null;
            Matcher hr = HAS_ROLE.matcher(window);
            if (hr.find()) { ruleType = "ROLE_CHECK"; roles = new ArrayList<>(List.of(hr.group(1))); }
            Matcher har = HAS_ANY_ROLE.matcher(window);
            if (roles == null && har.find()) {
                ruleType = "ROLE_CHECK";
                roles = stringLiterals(har.group(1));
            }
            Matcher ha = HAS_AUTHORITY.matcher(window);
            if (ruleType == null && ha.find()) { ruleType = "AUTHORITY_CHECK"; authorities = new ArrayList<>(List.of(ha.group(1))); }
            Matcher haa = HAS_ANY_AUTHORITY.matcher(window);
            if (ruleType == null && authorities == null && haa.find()) {
                ruleType = "AUTHORITY_CHECK";
                authorities = stringLiterals(haa.group(1));
            }
            if (ruleType == null && PERMIT_ALL.matcher(window).find()) ruleType = "PERMIT_ALL";
            if (ruleType == null && AUTHENTICATED.matcher(window).find()) ruleType = "AUTHENTICATED";

            for (String pattern : patterns) {
                rules.add(new SecurityEvidenceDto(fileName, line, line, ruleType, pattern, roles, authorities, null,
                        snippet(content, rm.start(), Math.min(window.length(), 120))));
            }
        }
    }

    private List<String> stringLiterals(String in) {
        List<String> out = new ArrayList<>();
        Matcher m = STRING_LITERALS.matcher(in);
        while (m.find()) out.add(m.group(1));
        return out;
    }

    private int lineNumberAt(String content, int index) {
        int line = 1;
        for (int i = 0; i < Math.min(index, content.length()); i++) {
            if (content.charAt(i) == '\n') line++;
        }
        return line;
    }

    private String snippet(String content, int start, int maxLen) {
        int end = Math.min(content.length(), start + maxLen);
        return content.substring(start, end).replace('\n', ' ').trim();
    }

    private String requestMappingPath(CtClass<?> ctClass) {
        for (CtAnnotation<?> a : ctClass.getAnnotations()) {
            if (a.getAnnotationType().getSimpleName().equals("RequestMapping")) {
                String v = a.getValueAsString("value");
                if (v == null || v.isBlank() || v.equals("\"\"")) return "";
                return v.replace("\"", "");
            }
        }
        return "";
    }

    private String pathOf(CtAnnotation<?> a, String defaultPath) {
        String v = a.getValueAsString("value");
        if (v == null) v = a.getValueAsString("path");
        if (v == null || v.isBlank() || v.equals("\"\"")) return defaultPath;
        return v.replace("\"", "");
    }

    private String joinPath(String base, String path) {
        if (path == null) return base == null ? "" : base;
        String b = base == null ? "" : base;
        String p = path == null ? "" : path;
        if (b.endsWith("/") && p.startsWith("/")) p = p.substring(1);
        String joined = b + p;
        if (!joined.startsWith("/")) joined = "/" + joined;
        return joined;
    }

    private List<Path> collectJavaFiles(String projectPath) {
        Path root = Path.of(projectPath);
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source path is not a directory: " + projectPath);
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !p.toString().contains(FileSystems.getDefault().getSeparator() + "target" + FileSystems.getDefault().getSeparator()))
                  .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot walk source tree: " + projectPath, e);
        }
        return files;
    }
}
