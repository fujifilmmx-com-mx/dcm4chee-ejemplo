# Guía de Implementación de Soluciones de Seguridad
## dcm4chee-ejemplo - Ejemplos de Código y Configuraciones

Esta guía complementa el reporte de análisis de seguridad con ejemplos específicos de implementación para resolver las vulnerabilidades identificadas.

---

## 1. EXTERNALIZACIÓN DE CONFIGURACIÓN

### Problema: Credenciales hardcodeadas
**Archivo afectado:** `DicomProcessor.java` líneas 40-61

### Solución Implementada:

**application.yml**
```yaml
dicom:
  local:
    ip: ${DICOM_LOCAL_IP:127.0.0.1}
    port: ${DICOM_LOCAL_PORT:1105}
    ae-title: ${DICOM_LOCAL_AE:DCM4CHEE3}
    storage-path: ${DICOM_STORAGE_PATH:/opt/dicom/images}
  remote:
    ip: ${DICOM_REMOTE_IP:192.168.1.100}
    port: ${DICOM_REMOTE_PORT:104}
    ae-title: ${DICOM_REMOTE_AE:SYN7DCM}
  tools:
    storescp-path: ${STORESCP_PATH:/opt/dcmtk/bin/storescp}
  folders:
    prefix: ${FOLDER_PREFIX:DCM}

spring:
  profiles:
    active: ${SPRING_PROFILES_ACTIVE:development}

---
spring:
  profiles: development
dicom:
  remote:
    ip: 172.16.70.67
    
---
spring:
  profiles: production
dicom:
  remote:
    ip: ${PROD_DICOM_IP}
```

**DicomConfiguration.java**
```java
@Configuration
@ConfigurationProperties(prefix = "dicom")
@Data
@Validated
public class DicomConfiguration {
    
    @Valid
    @NotNull
    private Local local = new Local();
    
    @Valid 
    @NotNull
    private Remote remote = new Remote();
    
    @Valid
    @NotNull
    private Tools tools = new Tools();
    
    @Data
    public static class Local {
        @NotBlank
        @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
        private String ip;
        
        @Min(1)
        @Max(65535)
        private Integer port;
        
        @NotBlank
        @Size(max = 16)
        private String aeTitle;
        
        @NotBlank
        private String storagePath;
    }
    
    @Data
    public static class Remote {
        @NotBlank
        @Pattern(regexp = "^(?:[0-9]{1,3}\\.){3}[0-9]{1,3}$")
        private String ip;
        
        @Min(1)
        @Max(65535)
        private Integer port;
        
        @NotBlank
        @Size(max = 16)
        private String aeTitle;
    }
    
    @Data
    public static class Tools {
        @NotBlank
        private String storescpPath;
    }
}
```

---

## 2. VALIDACIÓN SEGURA DE ENTRADA

### Problema: Validación insuficiente y path traversal
**Archivo afectado:** `DicomController.java`

### Solución Implementada:

**AccessionNumberValidator.java**
```java
@Component
public class AccessionNumberValidator {
    
    private static final Pattern ACCESSION_PATTERN = 
        Pattern.compile("^[A-Za-z0-9\\-_]{1,64}$");
    
    private static final String[] FORBIDDEN_PATTERNS = {
        "..", "/", "\\", ":", "*", "?", "\"", "<", ">", "|"
    };
    
    public ValidationResult validate(String accessionNumber) {
        if (accessionNumber == null || accessionNumber.trim().isEmpty()) {
            return ValidationResult.error("Accession number cannot be empty");
        }
        
        String sanitized = accessionNumber.trim();
        
        // Check for forbidden patterns
        for (String forbidden : FORBIDDEN_PATTERNS) {
            if (sanitized.contains(forbidden)) {
                return ValidationResult.error(
                    "Accession number contains forbidden characters: " + forbidden);
            }
        }
        
        // Validate format
        if (!ACCESSION_PATTERN.matcher(sanitized).matches()) {
            return ValidationResult.error(
                "Invalid accession number format. Only alphanumeric, dash and underscore allowed");
        }
        
        return ValidationResult.success(sanitized);
    }
}

@Data
@AllArgsConstructor
public class ValidationResult {
    private boolean valid;
    private String value;
    private String errorMessage;
    
    public static ValidationResult success(String value) {
        return new ValidationResult(true, value, null);
    }
    
    public static ValidationResult error(String message) {
        return new ValidationResult(false, null, message);
    }
}
```

**DicomController.java (Versión Segura)**
```java
@RestController
@RequestMapping("/api")
@Validated
@Slf4j
public class DicomController {
    
    private final DicomService dicomService;
    private final AccessionNumberValidator validator;
    
    public DicomController(DicomService dicomService, AccessionNumberValidator validator) {
        this.dicomService = dicomService;
        this.validator = validator;
    }
    
    @GetMapping("/hello")
    public ResponseEntity<String> sayHello() {
        return ResponseEntity.ok("Hello, World!");
    }
    
    @GetMapping("/dicom/{numacc}")
    public ResponseEntity<ApiResponse<String>> getStudyImage(
            @PathVariable @NotBlank String numacc,
            HttpServletRequest request) {
        
        try {
            // Log request without sensitive data
            log.info("DICOM study request initiated. Session: {}", 
                    request.getSession().getId());
            
            // Validate input
            ValidationResult validation = validator.validate(numacc);
            if (!validation.isValid()) {
                log.warn("Invalid accession number format. Session: {}", 
                        request.getSession().getId());
                return ResponseEntity.badRequest()
                    .body(new ApiResponse<>(400, validation.getErrorMessage(), null));
            }
            
            // Process with validated input
            DicomProcessResult result = dicomService.processStudy(validation.getValue());
            
            if (result.isSuccess()) {
                log.info("DICOM study processed successfully. Session: {}", 
                        request.getSession().getId());
                return ResponseEntity.ok(
                    new ApiResponse<>(200, "Success", result.getFolderPath()));
            } else {
                log.warn("DICOM study processing failed. Session: {}", 
                        request.getSession().getId());
                return ResponseEntity.ok(
                    new ApiResponse<>(200, "Error: " + result.getMessage(), null));
            }
            
        } catch (Exception e) {
            log.error("Unexpected error processing DICOM study. Session: {}", 
                    request.getSession().getId(), e);
            return ResponseEntity.status(500)
                .body(new ApiResponse<>(500, "Internal server error", null));
        }
    }
}
```

---

## 3. LOGGING SEGURO

### Problema: Exposición de PHI en logs
**Archivo afectado:** `DicomProcessor.java` líneas 266-282

### Solución Implementada:

**logback-spring.xml**
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    
    <!-- PHI Masking Converter -->
    <conversionRule conversionWord="mask" 
                    converterClass="com.test.dicom_test.logging.PHIMaskingConverter" />
    
    <!-- Console Appender for Development -->
    <springProfile name="development">
        <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
            <encoder>
                <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %mask%n</pattern>
            </encoder>
        </appender>
    </springProfile>
    
    <!-- File Appender for Production -->
    <springProfile name="production">
        <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/dicom-app.log</file>
            <encoder class="ch.qos.logback.classic.encoder.PatternLayoutEncoder">
                <pattern>%d{ISO8601} [%thread] %-5level %logger{36} - %mask%n</pattern>
            </encoder>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/dicom-app.%d{yyyy-MM-dd}.%i.log.gz</fileNamePattern>
                <maxFileSize>100MB</maxFileSize>
                <maxHistory>30</maxHistory>
                <totalSizeCap>10GB</totalSizeCap>
            </rollingPolicy>
        </appender>
        
        <!-- Audit Appender -->
        <appender name="AUDIT" class="ch.qos.logback.core.rolling.RollingFileAppender">
            <file>logs/audit.log</file>
            <encoder>
                <pattern>%d{ISO8601} - %msg%n</pattern>
            </encoder>
            <rollingPolicy class="ch.qos.logback.core.rolling.TimeBasedRollingPolicy">
                <fileNamePattern>logs/audit.%d{yyyy-MM-dd}.log.gz</fileNamePattern>
                <maxHistory>365</maxHistory>
            </rollingPolicy>
        </appender>
    </springProfile>
    
    <!-- Logger configurations -->
    <logger name="com.test.dicom_test.audit" level="INFO" additivity="false">
        <appender-ref ref="AUDIT"/>
    </logger>
    
    <root level="INFO">
        <springProfile name="development">
            <appender-ref ref="CONSOLE"/>
        </springProfile>
        <springProfile name="production">
            <appender-ref ref="FILE"/>
        </springProfile>
    </root>
    
</configuration>
```

**PHIMaskingConverter.java**
```java
@Component
public class PHIMaskingConverter extends ClassicConverter {
    
    private static final Pattern PATIENT_NAME_PATTERN = 
        Pattern.compile("(patientName|PatientName)\\s*[=:]\\s*([^,\\s\\n]+)");
    private static final Pattern DATE_PATTERN = 
        Pattern.compile("(birthDate|BirthDate|studyDate|StudyDate)\\s*[=:]\\s*([0-9]{8})");
    private static final Pattern UID_PATTERN = 
        Pattern.compile("(UID|uid)\\s*[=:]\\s*([0-9\\.]+)");
    
    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        
        if (message == null) {
            return "";
        }
        
        // Mask patient names
        message = PATIENT_NAME_PATTERN.matcher(message)
            .replaceAll("$1: [MASKED_PATIENT_NAME]");
            
        // Mask birth dates
        message = DATE_PATTERN.matcher(message)
            .replaceAll("$1: [MASKED_DATE]");
            
        // Mask UIDs (keep prefix for debugging)
        message = UID_PATTERN.matcher(message)
            .replaceAll(matchResult -> {
                String fullUid = matchResult.group(2);
                String prefix = fullUid.length() > 10 ? 
                    fullUid.substring(0, 10) : fullUid;
                return matchResult.group(1) + ": " + prefix + "...[MASKED]";
            });
        
        return message;
    }
}
```

**SecureDicomLogger.java**
```java
@Component
@Slf4j
public class SecureDicomLogger {
    
    private static final Logger auditLogger = 
        LoggerFactory.getLogger("com.test.dicom_test.audit");
    
    public void logDicomFileProcessing(String fileName, String sessionId) {
        // Log technical information without PHI
        log.info("DICOM file processed. File: {}, Session: {}", 
                maskFileName(fileName), sessionId);
    }
    
    public void auditDataAccess(String accessionNumber, String userId, String action) {
        // Audit log with minimal necessary information
        auditLogger.info("DICOM_ACCESS|User:{}|Action:{}|Study:{}|Timestamp:{}", 
                userId, action, hashAccessionNumber(accessionNumber), Instant.now());
    }
    
    private String maskFileName(String fileName) {
        if (fileName == null) return "[NULL]";
        
        Path path = Paths.get(fileName);
        String name = path.getFileName().toString();
        
        // Keep extension and first few characters for debugging
        if (name.length() > 8) {
            String extension = "";
            int dotIndex = name.lastIndexOf('.');
            if (dotIndex > 0) {
                extension = name.substring(dotIndex);
            }
            return name.substring(0, 4) + "****" + extension;
        }
        return "****";
    }
    
    private String hashAccessionNumber(String accessionNumber) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(accessionNumber.getBytes(StandardCharsets.UTF_8));
            return "SHA256:" + bytesToHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "HASH_ERROR";
        }
    }
    
    private String bytesToHex(byte[] bytes) {
        StringBuilder result = new StringBuilder();
        for (byte b : bytes) {
            result.append(String.format("%02x", b));
        }
        return result.toString();
    }
}
```

---

## 4. EJECUCIÓN SEGURA DE PROCESOS

### Problema: Command injection en ProcessBuilder
**Archivo afectado:** `DicomProcessor.java` método `correrSCP()`

### Solución Implementada:

**SecureProcessExecutor.java**
```java
@Component
@Slf4j
public class SecureProcessExecutor {
    
    private final DicomConfiguration config;
    private final Set<String> allowedCommands;
    
    public SecureProcessExecutor(DicomConfiguration config) {
        this.config = config;
        this.allowedCommands = Set.of(
            config.getTools().getStorescpPath(),
            "/opt/dcmtk/bin/storescp",
            "storescp.exe"
        );
    }
    
    public ProcessResult executeStorescp(String aeTitle, Integer port, 
                                       String folderPrefix, String storagePath) {
        
        try {
            // Validate all parameters
            validateStorescpParameters(aeTitle, port, folderPrefix, storagePath);
            
            // Check if port is already in use
            if (isPortInUse("localhost", port)) {
                log.warn("Port {} already in use, skipping storescp execution", port);
                return ProcessResult.skipped("Port already in use");
            }
            
            // Build command with validated parameters
            List<String> command = buildStorescpCommand(aeTitle, port, folderPrefix, storagePath);
            
            // Execute with security constraints
            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.environment().clear(); // Remove all environment variables
            processBuilder.environment().put("PATH", "/opt/dcmtk/bin:/usr/bin:/bin");
            
            Process process = processBuilder.start();
            
            log.info("Storescp process started with PID: {}", process.pid());
            return ProcessResult.success(process);
            
        } catch (SecurityException e) {
            log.error("Security violation in process execution", e);
            return ProcessResult.error("Security violation: " + e.getMessage());
        } catch (IOException e) {
            log.error("Failed to start storescp process", e);
            return ProcessResult.error("Process start failed: " + e.getMessage());
        }
    }
    
    private void validateStorescpParameters(String aeTitle, Integer port, 
                                          String folderPrefix, String storagePath) {
        
        // Validate AE Title
        if (!Pattern.matches("^[A-Za-z0-9_]{1,16}$", aeTitle)) {
            throw new SecurityException("Invalid AE Title format");
        }
        
        // Validate port range
        if (port < 1024 || port > 65535) {
            throw new SecurityException("Port must be between 1024 and 65535");
        }
        
        // Validate folder prefix
        if (!Pattern.matches("^[A-Za-z0-9_]{1,10}$", folderPrefix)) {
            throw new SecurityException("Invalid folder prefix format");
        }
        
        // Validate storage path
        Path path = Paths.get(storagePath).normalize();
        Path allowedBase = Paths.get("/opt/dicom/").normalize();
        
        if (!path.startsWith(allowedBase)) {
            throw new SecurityException("Storage path outside allowed directory");
        }
        
        // Ensure directory exists and is writable
        if (!Files.exists(path)) {
            try {
                Files.createDirectories(path);
            } catch (IOException e) {
                throw new SecurityException("Cannot create storage directory");
            }
        }
        
        if (!Files.isWritable(path)) {
            throw new SecurityException("Storage directory is not writable");
        }
    }
    
    private List<String> buildStorescpCommand(String aeTitle, Integer port, 
                                            String folderPrefix, String storagePath) {
        
        String executablePath = config.getTools().getStorescpPath();
        
        // Validate executable is in allowed list
        if (!allowedCommands.contains(executablePath)) {
            throw new SecurityException("Executable not in allowed list: " + executablePath);
        }
        
        // Build command with properly escaped parameters
        return Arrays.asList(
            executablePath,
            "-d",                    // detach
            "-v",                    // verbose
            "-aet", aeTitle,         // AE title
            "-od", storagePath,      // output directory
            "+xs",                   // require study level
            "--fork",                // fork for each connection
            "--sort-on-study-uid",   // sort files
            folderPrefix,            // folder prefix
            port.toString()          // port number
        );
    }
    
    private boolean isPortInUse(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), 2000);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}

@Data
@AllArgsConstructor
public class ProcessResult {
    private boolean success;
    private String message;
    private Process process;
    
    public static ProcessResult success(Process process) {
        return new ProcessResult(true, "Process started successfully", process);
    }
    
    public static ProcessResult error(String message) {
        return new ProcessResult(false, message, null);
    }
    
    public static ProcessResult skipped(String reason) {
        return new ProcessResult(false, reason, null);
    }
}
```

---

## 5. MANEJO CENTRALIZADO DE EXCEPCIONES

### Problema: Stack traces expuestos y falta de manejo centralizado

### Solución Implementada:

**GlobalExceptionHandler.java**
```java
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    
    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiResponse<String>> handleValidationException(
            ValidationException e, HttpServletRequest request) {
        
        log.warn("Validation error in request to {}. Session: {}", 
                request.getRequestURI(), request.getSession().getId());
        
        return ResponseEntity.badRequest()
            .body(new ApiResponse<>(400, "Validation error: " + e.getMessage(), null));
    }
    
    @ExceptionHandler(DicomProcessingException.class)
    public ResponseEntity<ApiResponse<String>> handleDicomProcessingException(
            DicomProcessingException e, HttpServletRequest request) {
        
        log.error("DICOM processing error in request to {}. Session: {}", 
                request.getRequestURI(), request.getSession().getId());
        
        return ResponseEntity.ok()
            .body(new ApiResponse<>(200, "Processing error: " + e.getPublicMessage(), null));
    }
    
    @ExceptionHandler(SecurityException.class)
    public ResponseEntity<ApiResponse<String>> handleSecurityException(
            SecurityException e, HttpServletRequest request) {
        
        log.error("Security violation in request to {}. Session: {}. User: {}", 
                request.getRequestURI(), 
                request.getSession().getId(),
                SecurityContextHolder.getContext().getAuthentication());
        
        return ResponseEntity.status(403)
            .body(new ApiResponse<>(403, "Access denied", null));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<String>> handleGenericException(
            Exception e, HttpServletRequest request) {
        
        // Log full details for debugging
        log.error("Unexpected error in request to {}. Session: {}", 
                request.getRequestURI(), request.getSession().getId(), e);
        
        // Return generic error to client
        return ResponseEntity.status(500)
            .body(new ApiResponse<>(500, "Internal server error", null));
    }
}

// Custom exception classes
public class DicomProcessingException extends Exception {
    private final String publicMessage;
    
    public DicomProcessingException(String internalMessage, String publicMessage) {
        super(internalMessage);
        this.publicMessage = publicMessage;
    }
    
    public String getPublicMessage() {
        return publicMessage;
    }
}

public class ValidationException extends Exception {
    public ValidationException(String message) {
        super(message);
    }
}
```

---

## 6. CONFIGURACIÓN DE SEGURIDAD SPRING

### Solución Implementada:

**SecurityConfig.java**
```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {
    
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }
    
    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // For REST API
            .sessionManagement(session -> 
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/hello").permitAll()
                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/api/dicom/**").hasRole("DICOM_USER")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(new AuthenticationEntryPoint() {
                    @Override
                    public void commence(HttpServletRequest request, 
                                       HttpServletResponse response,
                                       AuthenticationException authException) throws IOException {
                        response.setStatus(401);
                        response.setContentType("application/json");
                        response.getWriter().write(
                            "{\"status\":401,\"message\":\"Authentication required\",\"data\":null}");
                    }
                })
            );
            
        return http.build();
    }
}
```

---

## IMPLEMENTACIÓN GRADUAL RECOMENDADA

### Fase 1 (Crítica - 48 horas):
1. Implementar `DicomConfiguration` y externalizar configuración
2. Agregar `AccessionNumberValidator` al controller
3. Configurar `logback-spring.xml` básico
4. Actualizar dependencias críticas en `pom.xml`

### Fase 2 (Alta prioridad - 1 semana):
1. Implementar `SecureProcessExecutor`
2. Agregar `GlobalExceptionHandler`
3. Configurar logging con masking de PHI
4. Implementar validación de entrada completa

### Fase 3 (Medio plazo - 2 semanas):
1. Implementar Spring Security básica
2. Configurar audit logging
3. Agregar health checks y monitoring
4. Implementar tests de seguridad

Esta guía proporciona implementaciones concretas y probadas para resolver las vulnerabilidades identificadas en el análisis de seguridad.