package com.apixa.sourceanalysis.engine;

import com.apixa.common.model.SecurityPolicy;
import com.apixa.sourceanalysis.model.SecurityEvidenceDto;
import com.apixa.sourceanalysis.model.SourceAnalysisResultDto;
import com.apixa.sourceanalysis.model.SourceEndpointDto;
import org.springframework.stereotype.Component;
import spoon.Launcher;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtFieldRead;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtNewArray;
import spoon.reflect.cu.SourcePosition;
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
 *
 * <p>Three independent passes over the same source tree:
 * <ul>
 *   <li>{@link #analyzeEndpoints(String)} — Step 8 source endpoint analysis: the HTTP endpoints
 *       actually declared by Spring MVC/WebFlux controllers (paths, HTTP methods, controller class,
 *       Java method, source file and source line). Endpoint discovery only: it never reads security
 *       annotations, never reads SecurityFilterChain rules, never contacts the contract service and
 *       never consults an OpenAPI document.</li>
 *   <li>{@link #analyzeSecurity(String)} — Step 9 source security analysis: the security rules
 *       actually declared by the source (method-level {@code @PreAuthorize}/{@code @Secured}/
 *       {@code @RolesAllowed} and {@code SecurityFilterChain} authorization rules). Security
 *       discovery only: no OpenAPI comparison, no endpoint mapping, no MATCH/MISMATCH.</li>
 *   <li>{@link #analyze(String)} — pre-existing pass: the same endpoint discovery plus Spring Security
 *       rules (Step 9 material, kept unchanged in behaviour).</li>
 * </ul>
 * Both passes share the endpoint discovery code, so the mapping extraction cannot drift between them.
 * Extracts endpoint mappings and Spring Security rules with file/line evidence.
 * Independently testable: no HTTP, no persistence.
 */
@Component
public class SpringSourceAnalyzer {

    public static final String ANALYZER_VERSION = "spoon-1.0.0";

    /** {@code analysisType} for the pre-existing pass (endpoints + security rules). */
    public static final String ANALYSIS_TYPE_FULL = "FULL";
    /** {@code analysisType} for Step 8 source endpoint analysis (endpoints only, no security). */
    public static final String ANALYSIS_TYPE_ENDPOINTS = "ENDPOINTS";
    /** {@code analysisType} for Step 9 source security analysis (security rules only, no endpoints). */
    public static final String ANALYSIS_TYPE_SECURITY = "SECURITY";
    /** Analysis always completes synchronously; failures are raised as errors, never as a result. */
    public static final String STATUS_COMPLETED = "COMPLETED";
    /**
     * Marker for a {@code @RequestMapping} that declares no {@code method} attribute: Spring maps it
     * for all standard HTTP methods. One source declaration stays one record (nothing is invented).
     */
    public static final String HTTP_METHOD_ANY = "ANY";
    /** Marker for a {@code method} attribute that cannot be read from source without a classpath. */
    public static final String HTTP_METHOD_UNRESOLVED = "UNRESOLVED";

    /** Class-level annotations that make a type an endpoint controller. */
    private static final Set<String> CONTROLLER_ANNOTATIONS = Set.of("RestController", "Controller");
    /** Method-level shortcuts and the single HTTP method each one declares. */
    private static final Map<String, List<String>> MAPPING_ANNOTATIONS = Map.of(
            "GetMapping", List.of("GET"),
            "PostMapping", List.of("POST"),
            "PutMapping", List.of("PUT"),
            "DeleteMapping", List.of("DELETE"),
            "PatchMapping", List.of("PATCH"));
    private static final String REQUEST_MAPPING = "RequestMapping";
    /** {@code method} attribute names accepted for {@code @RequestMapping(method = ...)}. */
    private static final Set<String> REQUEST_METHODS = Set.of(
            "GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "TRACE");
    /** Spring aliases for the path attribute, resolved in Spring's own precedence order. */
    private static final String[] PATH_ATTRIBUTES = {"value", "path"};

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

    /**
     * Pre-existing full analysis: endpoint discovery plus Spring Security rules. Behaviour is
     * unchanged apart from the shared endpoint discovery improvements (class + method path
     * composition, {@code value}/{@code path} aliases, literal arrays, {@code RequestMethod}
     * expansion, mapping-annotation line evidence, deterministic ordering).
     */
    public SourceAnalysisResultDto analyze(String projectPath) {
        List<Path> javaFiles = collectJavaFiles(projectPath);
        List<SourceEndpointDto> endpoints = new ArrayList<>();
        List<SecurityEvidenceDto> legacyRules = new ArrayList<>();

        for (Path file : javaFiles) {
            analyzeFile(file, endpoints, legacyRules, true);
        }
        sortEndpoints(endpoints);
        return new SourceAnalysisResultDto(projectPath, ANALYSIS_TYPE_FULL, STATUS_COMPLETED, ANALYZER_VERSION,
                javaFiles.size(), endpoints.size(), endpoints, legacyRules, legacyRules.size());
    }

    /**
     * Step 9 source security analysis: the security rules actually declared by the Spring source.
     *
     * <p>Two independent sources, never mixed with endpoint paths and never compared with an
     * OpenAPI contract:
     * <ul>
     *   <li>method/class-level {@code @PreAuthorize}, {@code @Secured} and {@code @RolesAllowed},
     *       read with Spoon so scope, declaring class/method and the annotation line are exact;</li>
     *   <li>{@code SecurityFilterChain} authorization rules ({@code requestMatchers(...)} and
     *       {@code anyRequest()} followed by an authorization action), read textually because the
     *       fluent DSL rarely resolves without a full compile classpath.</li>
     * </ul>
     * Every rule carries file/line evidence. Output is sorted by file, line, scope, rule type,
     * matcher and operator; independent declarations are never merged.
     */
    public SourceAnalysisResultDto analyzeSecurity(String projectPath) {
        List<Path> files = collectJavaFiles(projectPath);
        Launcher launcher = buildModel(files);
        List<SecurityEvidenceDto> rules = new ArrayList<>();
        rules.addAll(extractAnnotationSecurity(launcher));
        rules.addAll(extractConfigRules(files));
        rules.sort(Comparator.comparing(SecurityEvidenceDto::file, Comparator.nullsLast(String::compareTo))
                .thenComparing(SecurityEvidenceDto::lineStart, Comparator.nullsLast(Integer::compareTo))
                .thenComparing(SecurityEvidenceDto::scope, Comparator.nullsLast(String::compareTo))
                .thenComparing(SecurityEvidenceDto::ruleType, Comparator.nullsLast(String::compareTo))
                .thenComparing(SecurityEvidenceDto::pathPattern, Comparator.nullsLast(String::compareTo))
                .thenComparing(SecurityEvidenceDto::operator, Comparator.nullsLast(String::compareTo)));
        String root = Path.of(projectPath).toAbsolutePath().normalize().toString();
        return new SourceAnalysisResultDto(root, ANALYSIS_TYPE_SECURITY, STATUS_COMPLETED,
                ANALYZER_VERSION, files.size(), 0, null,
                List.copyOf(rules), rules.size());
    }

    /**
     * Step 8 source endpoint analysis: discovers the HTTP endpoints declared by the Spring
     * controllers of a local project directory. Endpoint discovery only — source security analysis
     * (Step 9), OpenAPI comparison (Step 10/11) and any contract-service call are out of scope, so
     * every returned endpoint carries no security information at all. Must stay byte-identical to the
     * Step 8 verified behaviour: same endpointCount, same ordering, no security fields.
     */
    public SourceAnalysisResultDto analyzeEndpoints(String projectPath) {
        List<Path> javaFiles = collectJavaFiles(projectPath);
        List<SourceEndpointDto> endpoints = new ArrayList<>();

        for (Path file : javaFiles) {
            analyzeFile(file, endpoints, null, false);
        }
        sortEndpoints(endpoints);
        return new SourceAnalysisResultDto(projectPath, ANALYSIS_TYPE_ENDPOINTS, STATUS_COMPLETED, ANALYZER_VERSION,
                javaFiles.size(), endpoints.size(), endpoints, null, null);
    }

    // ---- Step 9: security rule extraction ----

    private static final Pattern SPEL_CALL = Pattern.compile(
            "(hasRole|hasAnyRole|hasAuthority|hasAnyAuthority|permitAll|authenticated|anonymous|denyAll|isAuthenticated|isAnonymous|isFullyAuthenticated)\\s*\\(");
    private static final Pattern LITERAL_ARGS = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"|'((?:[^'\\\\]|\\\\.)*)'");
    /* Legacy full-pass patterns below reuse STRING_LITERALS (double quotes); Step 9 extraction via
       Spoon/annotationValues handles single-quoted SpEL too. AUTH_ACTION sees only Step 9 helpers. */
    @SuppressWarnings("unused")
    private static final Pattern AUTH_ACTION = Pattern.compile(
            "\\.(hasRole|hasAnyRole|hasAuthority|hasAnyAuthority|permitAll|authenticated|denyAll|anonymous)\\s*\\(");
    private record ParsedCall(String ruleType, String operator, List<String> roles, List<String> authorities) {}

    private ParsedCall toRule(String call, List<String> literals) {
        return switch (call) {
            case "hasRole" -> new ParsedCall("ROLE", "HAS_ROLE", List.copyOf(literals), List.of());
            case "hasAnyRole" -> new ParsedCall("ROLE", "HAS_ANY_ROLE", List.copyOf(literals), List.of());
            case "hasAuthority" -> new ParsedCall("AUTHORITY", "HAS_AUTHORITY", List.of(), List.copyOf(literals));
            case "hasAnyAuthority" -> new ParsedCall("AUTHORITY", "HAS_ANY_AUTHORITY", List.of(), List.copyOf(literals));
            case "permitAll" -> new ParsedCall("PERMIT_ALL", "PERMIT_ALL", List.of(), List.of());
            case "authenticated", "isAuthenticated", "isFullyAuthenticated" ->
                    new ParsedCall("AUTHENTICATED", "AUTHENTICATED", List.of(), List.of());
            case "anonymous", "isAnonymous" -> new ParsedCall("ANONYMOUS", "ANONYMOUS", List.of(), List.of());
            case "denyAll" -> new ParsedCall("DENY_ALL", "DENY_ALL", List.of(), List.of());
            default -> new ParsedCall("EXPRESSION", "EXPRESSION", List.of(), List.of());
        };
    }

    private static String literalOf(Matcher m) {
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    private int findCloseParen(String s, int open) {
        int depth = 0;
        boolean inStr = false;
        char quote = 0;
        for (int i = open; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (c == quote && s.charAt(i - 1) != '\\') inStr = false;
            } else if (c == '\'' || c == '"') {
                inStr = true;
                quote = c;
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private boolean isComplexExpression(String expression, List<int[]> spans) {
        StringBuilder rest = new StringBuilder();
        int prev = 0;
        for (int[] span : spans) {
            int close = findCloseParen(expression, span[1] - 1);
            int end = close < 0 ? expression.length() : close + 1;
            rest.append(expression, prev, span[0]);
            prev = end;
        }
        rest.append(expression.substring(prev));
        String leftover = rest.toString().replaceAll("[\\s()!]+", "");
        return !leftover.isEmpty();
    }

    private List<SecurityEvidenceDto> extractAnnotationSecurity(Launcher launcher) {
        List<SecurityEvidenceDto> rules = new ArrayList<>();
        for (CtType<?> type : launcher.getModel().getAllTypes()) {
            if (!(type instanceof CtClass<?> cls)) continue;
            String file = sourceFileOf(cls);
            String className = cls.getSimpleName();
            for (CtAnnotation<?> ann : cls.getAnnotations()) {
                rules.addAll(annotationRules(ann, file, "CLASS", className, null));
            }
            for (CtMethod<?> method : cls.getElements(new TypeFilter<>(CtMethod.class))) {
                if (method.getDeclaringType() == null) continue;
                if (!method.getDeclaringType().getQualifiedName().equals(cls.getQualifiedName())) continue;
                for (CtAnnotation<?> ann : method.getAnnotations()) {
                    rules.addAll(annotationRules(ann, file, "METHOD", className, method.getSimpleName()));
                }
            }
        }
        return rules;
    }

    private List<SecurityEvidenceDto> annotationRules(CtAnnotation<?> ann, String file,
            String scope, String className, String methodName) {
        String name = ann.getAnnotationType().getSimpleName();
        return switch (name) {
            case "PreAuthorize", "PostAuthorize" -> preAuthorizeRules(ann, file, scope, className, methodName);
            case "Secured" -> securedRules(ann, file, scope, className, methodName);
            case "RolesAllowed" -> rolesAllowedRules(ann, file, scope, className, methodName);
            default -> List.of();
        };
    }

    private List<SecurityEvidenceDto> preAuthorizeRules(CtAnnotation<?> ann, String file,
            String scope, String className, String methodName) {
        int line = annotationLine(ann);
        CtExpression<?> declared = annotationValue(ann, "value");
        if (declared == null) return List.of();
        List<String> literals = new ArrayList<>();
        List<String> unres = new ArrayList<>();
        collectStringLiterals(declared, literals, unres);
        if (literals.isEmpty()) {
            String raw = snippetOf(ann.toString(), 200);
            return List.of(new SecurityEvidenceDto(file, line, line, "EXPRESSION", null,
                    List.of(), List.of(), null, raw,
                    scope, "EXPRESSION", raw, className, methodName, true, true, null));
        }
        // Mixed literal + non-literal (constant reference, concatenation): keep literals, flag unresolved.
        String expression = literals.get(0);
        Matcher calls = SPEL_CALL.matcher(expression);
        List<int[]> spans = new ArrayList<>();
        while (calls.find()) spans.add(new int[]{calls.start(), calls.end()});
        if (spans.isEmpty()) {
            return List.of(new SecurityEvidenceDto(file, line, line, "EXPRESSION", null,
                    List.of(), List.of(), null, snippetOf(expression, 200),
                    scope, "EXPRESSION", expression, className, methodName, true, true, null));
        }
        boolean complex = isComplexExpression(expression, spans);
        List<SecurityEvidenceDto> out = new ArrayList<>();
        for (int[] span : spans) {
            String call = expression.substring(span[0], span[1] - 1).trim();
            int paren = span[1] - 1;
            int close = findCloseParen(expression, paren);
            String args = close < 0 ? "" : expression.substring(span[1], close);
            List<String> callLiterals = new ArrayList<>();
            Matcher lit = LITERAL_ARGS.matcher(args);
            while (lit.find()) callLiterals.add(literalOf(lit));
            String stripped = LITERAL_ARGS.matcher(args).replaceAll("").replaceAll("[,\\s'\"]", "");
            boolean unresolved = !stripped.replace("()", "").isEmpty() || !unres.isEmpty();
            ParsedCall parsed = toRule(call, callLiterals);
            out.add(new SecurityEvidenceDto(file, line, line, parsed.ruleType(), null,
                    parsed.roles(), parsed.authorities(), null, snippetOf(expression, 200),
                    scope, parsed.operator(), expression, className, methodName,
                    complex, unresolved, null));
        }
        return out;
    }

    private List<SecurityEvidenceDto> annotationValuesRule(CtAnnotation<?> ann, String file,
            String scope, String className, String methodName, String operator) {
        int line = annotationLine(ann);
        CtExpression<?> declared = annotationValue(ann, "value");
        if (declared == null) return List.of();
        List<String> literals = new ArrayList<>();
        List<String> unres = new ArrayList<>();
        collectStringLiterals(declared, literals, unres);
        String raw = snippetOf(ann.toString(), 200);
        List<String> values = List.copyOf(literals);
        return List.of(new SecurityEvidenceDto(file, line, line, "ROLE", null,
                values.isEmpty() ? null : values, null, null, raw,
                scope, operator, raw, className, methodName, false, !unres.isEmpty(), null));
    }

    private List<SecurityEvidenceDto> securedRules(CtAnnotation<?> ann, String file,
            String scope, String className, String methodName) {
        return annotationValuesRule(ann, file, scope, className, methodName, "SECURED");
    }

    private List<SecurityEvidenceDto> rolesAllowedRules(CtAnnotation<?> ann, String file,
            String scope, String className, String methodName) {
        return annotationValuesRule(ann, file, scope, className, methodName, "ROLES_ALLOWED");
    }

    private String snippetOf(String text, int maxLen) {
        if (text == null) return null;
        String t = text.replace('\n', ' ').trim();
        return t.substring(0, Math.min(t.length(), maxLen));
    }

    private Launcher buildModel(List<Path> files) {
        Launcher launcher = new Launcher();
        for (Path file : files) launcher.addInputResource(file.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        try {
            launcher.buildModel();
        } catch (RuntimeException e) {
            throw new SourceParseException("Failed to parse Java source (" + oneLine(e.getMessage()) + ")");
        }
        return launcher;
    }

    private String sourceFileOf(CtClass<?> cls) {
        SourcePosition pos = cls.getPosition();
        if (pos != null && pos.getFile() != null) {
            try {
                return pos.getFile().toPath().toAbsolutePath().normalize().toString();
            } catch (RuntimeException e) {
                return pos.getFile().toString();
            }
        }
        return cls.getQualifiedName();
    }

    private int annotationLine(CtAnnotation<?> ann) {
        SourcePosition pos = ann.getPosition();
        return pos != null && pos.isValidPosition() ? pos.getLine() : 1;
    }

    /** String literals of the given attributes (Spoon arrays included); ROLE_ prefix kept as declared. */
    private List<String> annotationValues(CtAnnotation<?> ann, Set<String> attributes) {
        for (String attribute : attributes) {
            CtExpression<?> declared = annotationValue(ann, attribute);
            if (declared == null) continue;
            List<String> literals = new ArrayList<>();
            List<String> unresolved = new ArrayList<>();
            collectStringLiterals(declared, literals, unresolved);
            if (!literals.isEmpty() || !unresolved.isEmpty()) {
                if (!literals.isEmpty()) return List.copyOf(literals);
                return List.of();
            }
        }
        return List.of();
    }


    /** Deterministic order: path, HTTP method, source file, source line. Never traversal order. */
    private void sortEndpoints(List<SourceEndpointDto> endpoints) {
        endpoints.sort(Comparator
                .comparing(SourceEndpointDto::path, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SourceEndpointDto::httpMethod, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SourceEndpointDto::file, Comparator.nullsFirst(Comparator.naturalOrder()))
                .thenComparing(SourceEndpointDto::lineStart, Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /**
     * @param rules       security rule sink, or {@code null} for the endpoints-only pass
     * @param withSecurity when false no security annotation or SecurityFilterChain rule is read
     */
    private void analyzeFile(Path file, List<SourceEndpointDto> endpoints, List<SecurityEvidenceDto> rules,
                             boolean withSecurity) {
        String content;
        try {
            content = Files.readString(file);
        } catch (IOException e) {
            return; // unreadable file: skip silently, evidence would be unavailable anyway
        }
        if (withSecurity) {
            extractSecurityFilterRules(file.toString(), content, rules);
        }

        Launcher launcher = new Launcher();
        launcher.addInputResource(file.toString());
        launcher.getEnvironment().setNoClasspath(true);
        launcher.getEnvironment().setCommentEnabled(false);
        try {
            launcher.buildModel();
        } catch (RuntimeException e) {
            // malformed Java source is a client error; the parser stack trace is never exposed
            throw new SourceParseException("Failed to parse Java source: " + file + " (" + oneLine(e.getMessage()) + ")");
        }

        for (CtClass<?> ctClass : launcher.getModel().getElements(new TypeFilter<>(CtClass.class))) {
            if (isController(ctClass)) {
                extractEndpoints(ctClass, file.toString(), endpoints, rules, withSecurity);
            }
        }
    }

    private boolean isController(CtClass<?> ctClass) {
        return ctClass.getAnnotations().stream()
                .anyMatch(a -> CONTROLLER_ANNOTATIONS.contains(a.getAnnotationType().getSimpleName()));
    }

    /**
     * Discovers the endpoints declared by one controller class. Class-level and method-level paths
     * are composed as Spring composes them; multi-path annotations produce one record per declared
     * path, and two source declarations with the same method and path are both kept (Step 10 mapping
     * needs them for ambiguity detection).
     */
    private void extractEndpoints(CtClass<?> ctClass, String fileName, List<SourceEndpointDto> endpoints,
                                  List<SecurityEvidenceDto> rules, boolean withSecurity) {
        PathSpec classPaths = pathSpec(classMapping(ctClass));
        List<String> bases = classPaths.paths().isEmpty() ? List.of("") : classPaths.paths();
        String controllerPackage = ctClass.getPackage() == null ? null : ctClass.getPackage().getQualifiedName();

        for (Object o : ctClass.getMethods()) {
            CtMethod<?> method = (CtMethod<?>) o;
            CtAnnotation<?> mapping = mappingAnnotation(method);
            if (mapping == null) continue;

            PathSpec methodPaths = pathSpec(mapping);
            List<String> declaredPaths = methodPaths.paths().isEmpty() ? List.of("") : methodPaths.paths();

            List<String> unresolved = new ArrayList<>();
            unresolved.addAll(classPaths.unresolved());
            unresolved.addAll(methodPaths.unresolved());
            boolean pathResolved = unresolved.isEmpty();
            List<String> httpMethods = httpMethodsOf(mapping, unresolved);
            Integer line = mappingLine(mapping, method);

            for (String base : bases) {
                for (String declared : declaredPaths) {
                    for (String httpMethod : httpMethods) {
                        String fullPath = joinPath(base, declared);
                        SecurityPolicy policy = null;
                        List<SecurityEvidenceDto> evidence = null;
                        if (withSecurity) {
                            evidence = new ArrayList<>();
                            policy = methodSecurity(method, fileName, evidence);
                            if (policy == null) {
                                policy = configPolicyFor(fullPath, rules, evidence);
                            }
                            if (policy == null) policy = SecurityPolicy.UNKNOWN;
                        }
                        endpoints.add(new SourceEndpointDto(ctClass.getSimpleName(), method.getSimpleName(), httpMethod,
                                fullPath, fileName, line, policy, evidence, controllerPackage,
                                method.getParameters().size(), pathResolved,
                                unresolved.isEmpty() ? null : List.copyOf(unresolved)));
                    }
                }
            }
        }
    }

    /** HTTP methods declared by a mapping annotation. Never invents a method Spring would not map. */
    private List<String> httpMethodsOf(CtAnnotation<?> mapping, List<String> unresolved) {
        List<String> shortcut = MAPPING_ANNOTATIONS.get(mapping.getAnnotationType().getSimpleName());
        if (shortcut != null) return shortcut;

        CtExpression<?> declared = annotationValue(mapping, "method");
        if (declared == null) return List.of(HTTP_METHOD_ANY); // Spring maps every standard method
        List<String> methods = new ArrayList<>();
        List<String> methodUnresolved = new ArrayList<>();
        collectRequestMethods(declared, methods, methodUnresolved);
        unresolved.addAll(methodUnresolved);
        if (!methods.isEmpty()) return methods;
        return methodUnresolved.isEmpty() ? List.of(HTTP_METHOD_ANY) : List.of(HTTP_METHOD_UNRESOLVED);
    }

    /** Resolved path literals of one annotation plus every expression that could not be resolved. */
    private record PathSpec(List<String> paths, List<String> unresolved) {}

    /**
     * Reads the path of a mapping annotation using Spring's {@code value}/{@code path} alias
     * precedence. A missing or empty attribute means "no path", so the other level's path is used
     * unchanged. Non literal expressions (constant references, concatenation) are reported through
     * {@link PathSpec#unresolved()} instead of being guessed.
     */
    private PathSpec pathSpec(CtAnnotation<?> mapping) {
        if (mapping == null) return new PathSpec(List.of(), List.of());
        for (String attribute : PATH_ATTRIBUTES) {
            CtExpression<?> declared = annotationValue(mapping, attribute);
            if (declared == null) continue;
            List<String> paths = new ArrayList<>();
            List<String> unresolved = new ArrayList<>();
            collectStringLiterals(declared, paths, unresolved);
            if (paths.isEmpty() && unresolved.isEmpty()) {
                continue; // attribute carries no path (absent or empty array): try the next alias
            }
            return new PathSpec(List.copyOf(paths), List.copyOf(unresolved));
        }
        return new PathSpec(List.of(), List.of());
    }

    private CtExpression<?> annotationValue(CtAnnotation<?> mapping, String attribute) {
        try {
            return mapping.getValue(attribute);
        } catch (RuntimeException e) {
            return null; // attribute cannot be read from source without a classpath
        }
    }

    private void collectStringLiterals(CtExpression<?> expression, List<String> literals, List<String> unresolved) {
        if (expression instanceof CtNewArray<?> array) {
            for (CtExpression<?> element : array.getElements()) collectStringLiterals(element, literals, unresolved);
            return;
        }
        if (expression instanceof CtLiteral<?> literal && literal.getValue() instanceof String value) {
            literals.add(value);
            return;
        }
        unresolved.add(oneLine(expression.toString()));
    }

    private void collectRequestMethods(CtExpression<?> expression, List<String> methods, List<String> unresolved) {
        if (expression instanceof CtNewArray<?> array) {
            for (CtExpression<?> element : array.getElements()) collectRequestMethods(element, methods, unresolved);
            return;
        }
        if (expression instanceof CtFieldRead<?> fieldRead) {
            String name = fieldRead.getVariable() == null ? null : fieldRead.getVariable().getSimpleName();
            if (name != null && REQUEST_METHODS.contains(name)) {
                methods.add(name);
                return;
            }
        }
        unresolved.add(oneLine(expression.toString()));
    }

    /**
     * Reads a string-valued annotation attribute without ever throwing: array values (for example
     * {@code @Secured({"ROLE_A", "ROLE_B"})}) are joined, unresolvable or default-only attributes
     * yield {@code null}. Replaces Spoon's {@code getValueAsString}, which throws
     * {@link ClassCastException} when the attribute is an array.
     */
    private String literalAnnotationValue(CtAnnotation<?> annotation, String attribute) {
        CtExpression<?> expression = annotationValue(annotation, attribute);
        if (expression == null) return null;
        List<String> literals = new ArrayList<>();
        List<String> unresolved = new ArrayList<>();
        collectStringLiterals(expression, literals, unresolved);
        if (literals.isEmpty()) return null;
        return literals.size() == 1 ? literals.get(0) : String.join(",", literals);
    }

    /** First mapping annotation declared on the method, or null when the method is not an endpoint. */
    private CtAnnotation<?> mappingAnnotation(CtMethod<?> method) {
        for (CtAnnotation<?> a : method.getAnnotations()) {
            String name = a.getAnnotationType().getSimpleName();
            if (MAPPING_ANNOTATIONS.containsKey(name) || REQUEST_MAPPING.equals(name)) return a;
        }
        return null;
    }

    private CtAnnotation<?> classMapping(CtClass<?> ctClass) {
        for (CtAnnotation<?> a : ctClass.getAnnotations()) {
            if (REQUEST_MAPPING.equals(a.getAnnotationType().getSimpleName())) return a;
        }
        return null;
    }

    /** 1-based line of the mapping declaration itself, falling back to the method declaration line. */
    private Integer mappingLine(CtAnnotation<?> mapping, CtMethod<?> method) {
        SourcePosition position = mapping.getPosition();
        if (position != null && position.isValidPosition()) return position.getLine();
        SourcePosition methodPosition = method.getPosition();
        return methodPosition != null && methodPosition.isValidPosition() ? methodPosition.getLine() : null;
    }

    /** Single-line, length-capped message text: parser problems are never dumped verbatim. */
    private static String oneLine(String text) {
        if (text == null || text.isBlank()) return "unresolved expression";
        String collapsed = text.replaceAll("\\s+", " ").trim();
        return collapsed.length() > 200 ? collapsed.substring(0, 200) + "..." : collapsed;
    }

    /** Raised when a supplied project contains Java source that cannot be parsed (client error). */
    public static class SourceParseException extends RuntimeException {
        public SourceParseException(String message) { super(message); }
    }

    private SecurityPolicy methodSecurity(CtMethod<?> method, String fileName, List<SecurityEvidenceDto> evidence) {
        for (CtAnnotation<?> a : method.getAnnotations()) {
            String n = a.getAnnotationType().getSimpleName();
            int line = a.getPosition() != null ? a.getPosition().getLine() : method.getPosition().getLine();
            switch (n) {
                case "PreAuthorize" -> {
                    String value = literalAnnotationValue(a, "value");
                    if (value != null) {
                        List<String> roles = new ArrayList<>();
                        List<String> authorities = new ArrayList<>();
                        Matcher m = PRE_AUTHORIZE_ROLE.matcher(value);
                        while (m.find()) roles.add(m.group(1).startsWith("ROLE_") ? m.group(1).substring(5) : m.group(1));
                        m = PRE_AUTHORIZE_AUTH.matcher(value);
                        while (m.find()) authorities.add(m.group(1));
                        if (PRE_AUTHORIZE_PERMIT.matcher(value).find()) {
                            evidence.add(new SecurityEvidenceDto(fileName, line, line, "PERMIT_ALL", null, null, null, null, value));
                            return SecurityPolicy.permitAllPolicy();
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
                    String value = literalAnnotationValue(a, "value");
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
                if ("PERMIT_ALL".equals(rule.ruleType())) return SecurityPolicy.permitAllPolicy();
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
            if (nextRm.find()) windowEnd = Math.min(windowEnd, nextRm.start());
            if (nextAny.find()) windowEnd = Math.min(windowEnd, nextAny.start());
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

    /**
     * Step 9 per-file config rules for the SECURITY pass. Reuses the legacy
     * {@code extractSecurityFilterRules} scan, then enriches each rule with the Step 9 additive
     * fields ({@code SECURITY_CONFIGURATION} scope, precise operator, unresolved marker).
     */
    private List<SecurityEvidenceDto> extractConfigRules(List<Path> files) {
        List<SecurityEvidenceDto> enriched = new ArrayList<>();
        for (Path file : files) {
            String content;
            try {
                content = Files.readString(file);
            } catch (IOException e) {
                continue;
            }
            if (!content.contains("requestMatchers") && !content.contains("anyRequest")) continue;
            String fileName = file.toAbsolutePath().normalize().toString();
            List<SecurityEvidenceDto> legacy = new ArrayList<>();
            extractSecurityFilterRules(fileName, content, legacy);
            for (SecurityEvidenceDto rule : legacy) {
                String raw = snippetOf(rule.sourceSnippet(), 200);
                enriched.add(new SecurityEvidenceDto(rule.file(), rule.lineStart(), rule.lineEnd(),
                        normalizedType(rule), rule.pathPattern(), rule.roles(), rule.authorities(),
                        rule.scopes(), raw, "SECURITY_CONFIGURATION",
                        operatorFor(rule), rule.sourceSnippet(), null, null, false,
                        rule.ruleType() == null, "**".equals(rule.pathPattern())));
            }
        }
        return enriched;
    }

    private String operatorFor(SecurityEvidenceDto rule) {
        if (rule.roles() != null && !rule.roles().isEmpty()) {
            return rule.roles().size() > 1 ? "HAS_ANY_ROLE" : "HAS_ROLE";
        }
        if (rule.authorities() != null && !rule.authorities().isEmpty()) {
            return rule.authorities().size() > 1 ? "HAS_ANY_AUTHORITY" : "HAS_AUTHORITY";
        }
        return switch (rule.ruleType() == null ? "" : rule.ruleType()) {
            case "PERMIT_ALL" -> "PERMIT_ALL";
            case "AUTHENTICATED" -> "AUTHENTICATED";
            case "ANONYMOUS" -> "ANONYMOUS";
            case "DENY_ALL" -> "DENY_ALL";
            default -> null;
        };
    }

    /** Step 9 canonical type; legacy FULL-pass values stay untouched. */
    private String normalizedType(SecurityEvidenceDto rule) {
        if (rule.roles() != null && !rule.roles().isEmpty()) return "ROLE";
        if (rule.authorities() != null && !rule.authorities().isEmpty()) return "AUTHORITY";
        return rule.ruleType();
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

    /**
     * Composes a class-level and a method-level path the way Spring does, so
     * {@code @RequestMapping("/api/users")} plus {@code @GetMapping} yields {@code /api/users} and
     * plus {@code @GetMapping("/{id}")} yields {@code /api/users/{id}}. Path syntax is preserved
     * verbatim — normalisation for mapping against a contract belongs to Step 10.
     */
    private String joinPath(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null ? "" : path.trim();
        if (b.equals("/")) b = "";
        if (p.equals("/")) p = "";
        if (p.startsWith("/") && b.endsWith("/")) {
            p = p.substring(1);
        } else if (!p.isEmpty() && !p.startsWith("/") && !b.isEmpty() && !b.endsWith("/")) {
            p = "/" + p;
        }
        String joined = b + p;
        if (joined.isEmpty()) return "/";
        return joined.startsWith("/") ? joined : "/" + joined;
    }

    /**
     * Collects every {@code .java} file below the project directory (build output is skipped). The
     * root is normalized, so the reported source evidence is identical for relative and absolute
     * input paths and does not depend on the working directory.
     */
    private List<Path> collectJavaFiles(String projectPath) {
        Path root;
        try {
            root = Path.of(projectPath).toAbsolutePath().normalize();
        } catch (RuntimeException e) { // InvalidPathException
            throw new IllegalArgumentException("Source path is not a valid directory path: " + projectPath);
        }
        if (!Files.isDirectory(root)) {
            throw new IllegalArgumentException("Source path is not a directory: " + projectPath);
        }
        String separator = FileSystems.getDefault().getSeparator();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(p -> p.toString().endsWith(".java"))
                  .filter(p -> !p.toString().contains(separator + "target" + separator))
                  .forEach(files::add);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot walk source tree: " + projectPath, e);
        }
        return files;
    }
}
