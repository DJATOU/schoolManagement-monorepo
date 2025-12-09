# 📐 Plan de Refactoring - School Management System

## Vue d'ensemble

Basé sur l'analyse architecturale complète, ce document propose un plan de refactoring structuré en **3 phases** pour transformer le code existant en une architecture production-ready, maintenable et scalable.

---

## 🎯 Objectifs du Refactoring

### Objectifs Principaux
1. **Éliminer les anti-patterns** (ApplicationContextProvider dans les mappers)
2. **Améliorer la séparation des responsabilités** (Services trop gros, Controllers avec logique)
3. **Renforcer la sécurité** (Authentication, Authorization, CORS configurables)
4. **Optimiser les performances** (Pagination, Caching, N+1 queries)
5. **Faciliter les tests** (Réduire le couplage, Dependency Injection propre)

### Métriques de Succès
- ✅ 0 anti-pattern restant
- ✅ Tous les services < 300 LOC
- ✅ 0 repository direct dans les controllers
- ✅ Couverture de tests > 70%
- ✅ Tous les endpoints paginés
- ✅ Authentication/Authorization implémentée

---

## 🏗️ Nouvelle Architecture Proposée

### Organisation des Packages (Refactorée)

```
com.school.management/
│
├── 📦 api/                              [NOUVEAU - API Layer]
│   ├── rest/                           [REST Controllers]
│   │   ├── student/
│   │   │   └── StudentController.java
│   │   ├── payment/
│   │   │   └── PaymentController.java
│   │   ├── group/
│   │   │   └── GroupController.java
│   │   └── ...
│   │
│   ├── request/                        [Request DTOs - Input]
│   │   ├── student/
│   │   │   ├── CreateStudentRequest.java
│   │   │   └── UpdateStudentRequest.java
│   │   ├── payment/
│   │   │   ├── ProcessPaymentRequest.java
│   │   │   └── CatchUpPaymentRequest.java
│   │   └── ...
│   │
│   └── response/                       [Response DTOs - Output]
│       ├── student/
│       │   └── StudentResponse.java
│       ├── payment/
│       │   ├── PaymentResponse.java
│       │   └── PaymentStatusResponse.java
│       ├── common/
│       │   ├── PageResponse.java      [Generic pagination wrapper]
│       │   ├── ApiResponse.java       [Standard API response]
│       │   └── ErrorResponse.java     [Standard error response]
│       └── ...
│
├── 📦 domain/                           [REFACTORÉ - Domain Layer]
│   ├── model/                          [Entities déplacées depuis persistance/]
│   │   ├── base/
│   │   │   ├── BaseEntity.java
│   │   │   └── PersonEntity.java
│   │   ├── student/
│   │   │   ├── Student.java           [Renommé depuis StudentEntity]
│   │   │   ├── StudentGroup.java
│   │   │   └── Tutor.java
│   │   ├── group/
│   │   │   ├── Group.java
│   │   │   ├── GroupType.java
│   │   │   └── Level.java
│   │   ├── session/
│   │   │   ├── Session.java
│   │   │   ├── SessionSeries.java
│   │   │   └── Attendance.java
│   │   ├── payment/
│   │   │   ├── Payment.java
│   │   │   ├── PaymentDetail.java
│   │   │   └── Pricing.java
│   │   └── teacher/
│   │       └── Teacher.java
│   │
│   ├── repository/                     [Repositories inchangés]
│   │   ├── student/
│   │   │   ├── StudentRepository.java
│   │   │   └── TutorRepository.java
│   │   ├── payment/
│   │   │   ├── PaymentRepository.java
│   │   │   └── PaymentDetailRepository.java
│   │   └── ...
│   │
│   └── valueobject/                    [NOUVEAU - Value Objects]
│       ├── Money.java                  [Encapsule montants]
│       ├── Email.java                  [Validation email]
│       ├── PhoneNumber.java
│       └── DateRange.java
│
├── 📦 application/                      [NOUVEAU - Application Services]
│   ├── service/
│   │   ├── student/
│   │   │   ├── StudentService.java           [CRUD de base]
│   │   │   ├── StudentSearchService.java     [Recherche]
│   │   │   ├── StudentHistoryService.java    [Historique]
│   │   │   └── StudentEnrollmentService.java [NOUVEAU - Inscription]
│   │   │
│   │   ├── payment/
│   │   │   ├── PaymentService.java           [DIVISÉ depuis original]
│   │   │   ├── PaymentProcessingService.java [NOUVEAU - Traitement]
│   │   │   ├── PaymentStatusService.java     [NOUVEAU - Statut]
│   │   │   └── PaymentDistributionService.java [NOUVEAU - Distribution]
│   │   │
│   │   ├── group/
│   │   │   ├── GroupService.java
│   │   │   └── GroupSearchService.java
│   │   │
│   │   ├── session/
│   │   │   └── SessionService.java
│   │   │
│   │   └── notification/               [NOUVEAU]
│   │       └── NotificationService.java
│   │
│   ├── mapper/                         [REFACTORÉ - Plus d'accès aux repos]
│   │   ├── StudentMapper.java
│   │   ├── PaymentMapper.java
│   │   ├── GroupMapper.java
│   │   └── ...
│   │
│   └── dto/                            [DTOs internes au service layer]
│       ├── StudentDTO.java
│       ├── PaymentDTO.java
│       └── ...
│
├── 📦 infrastructure/                   [NOUVEAU - Infrastructure Layer]
│   ├── config/
│   │   ├── security/
│   │   │   ├── SecurityConfig.java
│   │   │   ├── JwtAuthenticationFilter.java [NOUVEAU]
│   │   │   ├── JwtTokenProvider.java        [NOUVEAU]
│   │   │   └── CorsConfig.java              [NOUVEAU - Externalisé]
│   │   ├── database/
│   │   │   ├── DatabaseConfig.java
│   │   │   └── JpaAuditConfig.java          [NOUVEAU - Audit]
│   │   ├── cache/
│   │   │   └── CacheConfig.java             [NOUVEAU - Redis/Caffeine]
│   │   ├── async/
│   │   │   └── AsyncConfig.java             [NOUVEAU]
│   │   └── web/
│   │       ├── WebMvcConfig.java
│   │       └── PaginationConfig.java        [NOUVEAU]
│   │
│   ├── storage/                        [File storage abstraction]
│   │   ├── FileStorageService.java    [Interface]
│   │   ├── LocalFileStorageService.java
│   │   └── S3FileStorageService.java  [NOUVEAU - Pour cloud]
│   │
│   ├── email/                          [NOUVEAU - Email service]
│   │   ├── EmailService.java
│   │   └── impl/
│   │       └── SmtpEmailService.java
│   │
│   └── pdf/
│       └── PdfGeneratorService.java
│
├── 📦 shared/                           [NOUVEAU - Shared/Common]
│   ├── exception/
│   │   ├── BusinessException.java           [NOUVEAU - Base]
│   │   ├── ResourceNotFoundException.java   [NOUVEAU]
│   │   ├── ValidationException.java         [NOUVEAU]
│   │   ├── CustomServiceException.java
│   │   ├── GroupAlreadyAssociatedException.java
│   │   └── GlobalExceptionHandler.java
│   │
│   ├── validation/
│   │   ├── FileValidationUtil.java
│   │   ├── ValidationGroups.java            [NOUVEAU - Validation groups]
│   │   └── constraint/                      [NOUVEAU - Custom constraints]
│   │       ├── ValidPhone.java
│   │       └── ValidEmail.java
│   │
│   ├── util/
│   │   ├── DateUtil.java                    [NOUVEAU]
│   │   ├── StringUtil.java                  [NOUVEAU]
│   │   └── CollectionUtil.java              [NOUVEAU]
│   │
│   └── constant/
│       ├── ErrorCode.java                   [NOUVEAU]
│       └── AppConstants.java                [NOUVEAU]
│
└── 📦 SchoolManagementApplication.java

```

---

## 📋 Phase 1 : Corrections Critiques (Semaine 1-2)

### 1.1 Éliminer l'Anti-Pattern ApplicationContextProvider

**Problème Actuel :**
```java
// StudentMapper.java - ANTI-PATTERN
@Named("loadLevelEntity")
default LevelEntity loadLevelEntity(Long id) {
    return ApplicationContextProvider
        .getBean(LevelRepository.class)
        .findById(id)
        .orElseThrow(() -> new CustomServiceException("Level not found"));
}
```

**Solution Proposée :**

**Étape 1 : Créer MappingContext**
```java
// shared/mapper/MappingContext.java
package com.school.management.shared.mapper;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * Context pour passer des dépendances aux mappers
 * sans violer le principe de responsabilité unique
 */
@Data
@AllArgsConstructor
public class MappingContext {
    private final LevelRepository levelRepository;
    private final TutorRepository tutorRepository;
    private final GroupRepository groupRepository;
    // Ajouter d'autres repositories nécessaires

    // Factory method
    public static MappingContext of(
        LevelRepository levelRepository,
        TutorRepository tutorRepository,
        GroupRepository groupRepository) {
        return new MappingContext(levelRepository, tutorRepository, groupRepository);
    }
}
```

**Étape 2 : Refactorer StudentMapper**
```java
// application/mapper/StudentMapper.java
package com.school.management.application.mapper;

import com.school.management.shared.mapper.MappingContext;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface StudentMapper {

    /**
     * Convertit Student vers StudentDTO
     * Pas besoin de contexte pour cette direction (lecture)
     */
    StudentDTO toDTO(Student student);

    /**
     * Convertit StudentDTO vers Student
     * Nécessite contexte pour résoudre les relations
     *
     * @param dto le DTO source
     * @param context contexte contenant les repositories
     * @return l'entité Student hydratée
     */
    @Mapping(target = "level", source = "levelId", qualifiedByName = "loadLevel")
    @Mapping(target = "tutor", source = "tutorId", qualifiedByName = "loadTutor")
    Student toEntity(StudentDTO dto, @Context MappingContext context);

    /**
     * Met à jour une entité existante avec les données du DTO
     */
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(StudentDTO dto, @MappingTarget Student entity, @Context MappingContext context);

    /**
     * Résout Level depuis son ID
     * Utilise le contexte passé en paramètre
     */
    @Named("loadLevel")
    default Level loadLevel(Long levelId, @Context MappingContext context) {
        if (levelId == null) {
            return null;
        }
        return context.getLevelRepository()
            .findById(levelId)
            .orElseThrow(() -> new ResourceNotFoundException("Level", levelId));
    }

    /**
     * Résout Tutor depuis son ID
     */
    @Named("loadTutor")
    default Tutor loadTutor(Long tutorId, @Context MappingContext context) {
        if (tutorId == null) {
            return null;
        }
        return context.getTutorRepository()
            .findById(tutorId)
            .orElseThrow(() -> new ResourceNotFoundException("Tutor", tutorId));
    }
}
```

**Étape 3 : Adapter StudentService**
```java
// application/service/student/StudentService.java
package com.school.management.application.service.student;

@Service
@RequiredArgsConstructor
public class StudentService {

    private final StudentRepository studentRepository;
    private final StudentMapper studentMapper;

    // Repositories pour le mapping context
    private final LevelRepository levelRepository;
    private final TutorRepository tutorRepository;
    private final GroupRepository groupRepository;

    /**
     * Crée le contexte de mapping une seule fois
     * Réutilisable dans toutes les méthodes
     */
    @PostConstruct
    private void initMappingContext() {
        this.mappingContext = MappingContext.of(
            levelRepository,
            tutorRepository,
            groupRepository
        );
    }

    private MappingContext mappingContext;

    @Transactional
    public StudentDTO createStudent(StudentDTO dto) {
        // Utilise le mapper avec le contexte
        Student entity = studentMapper.toEntity(dto, mappingContext);

        Student saved = studentRepository.save(entity);

        return studentMapper.toDTO(saved);
    }

    @Transactional
    public StudentDTO updateStudent(Long id, StudentDTO dto) {
        Student existing = studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", id));

        // Mise à jour avec contexte
        studentMapper.updateEntity(dto, existing, mappingContext);

        Student updated = studentRepository.save(existing);

        return studentMapper.toDTO(updated);
    }
}
```

**Étape 4 : Supprimer ApplicationContextProvider**
```bash
# Fichier à SUPPRIMER complètement
rm src/main/java/com/school/management/mapper/ApplicationContextProvider.java
```

**Impact :**
- ✅ Élimine l'anti-pattern Service Locator
- ✅ Rend les mappers testables unitairement
- ✅ Dépendances explicites et contrôlées
- ✅ Pas de couplage caché

---

### 1.2 Ajouter @Transactional sur PaymentService.processPayment()

**Problème :**
```java
// Actuel - PAS de @Transactional
public PaymentEntity processPayment(Long studentId, Long groupId, Long sessionSeriesId, double amountPaid) {
    // Multiple repository calls sans garantie atomique
    Student student = studentRepository.findById(studentId).orElseThrow();
    Group group = groupRepository.findById(groupId).orElseThrow();
    // ... plus de 50 lignes d'opérations DB
    paymentRepository.save(payment);
    paymentDetailRepository.saveAll(details);
    // Si l'une échoue, état incohérent !
}
```

**Solution :**
```java
@Transactional  // AJOUTER CETTE ANNOTATION
public PaymentEntity processPayment(Long studentId, Long groupId, Long sessionSeriesId, double amountPaid) {
    // Maintenant ATOMIC - tout ou rien
    // ...
}

@Transactional  // Sur toutes les méthodes de modification
public PaymentEntity processCatchUpPayment(...) {
    // ...
}
```

---

### 1.3 Extraire FileManagementService depuis StudentController

**Problème Actuel :**
```java
// StudentController.java - Lignes 62-113
@PostMapping("/createStudent")
public ResponseEntity<Object> createStudent(@Valid @ModelAttribute StudentDTO studentDto,
                                       @RequestParam("file") MultipartFile file) {
    // BUSINESS LOGIC dans le controller
    FileValidationUtil.validateImageFile(file);
    String fileName = FileValidationUtil.generateSafeFilename(file.getOriginalFilename());
    Path filePath = null;

    try {
        Path uploadPath = Paths.get(uploadDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        filePath = uploadPath.resolve(fileName);
        Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
        // ... 50 lignes de logique métier
    }
}
```

**Solution : Créer FileManagementService**

```java
// infrastructure/storage/FileManagementService.java
package com.school.management.infrastructure.storage;

@Service
@Slf4j
public class FileManagementService {

    private final FileStorageService fileStorageService;

    public FileManagementService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    /**
     * Upload un fichier et retourne le nom du fichier sauvegardé
     *
     * @param file le fichier à uploader
     * @return le nom du fichier sauvegardé
     * @throws IOException si l'upload échoue
     */
    public String uploadFile(MultipartFile file) throws IOException {
        log.debug("Uploading file: {}", file.getOriginalFilename());

        // Validation
        FileValidationUtil.validateImageFile(file);

        // Sauvegarde via l'abstraction
        String savedFileName = fileStorageService.saveFile(file);

        log.info("File uploaded successfully: {}", savedFileName);
        return savedFileName;
    }

    /**
     * Récupère un fichier
     */
    public Resource getFile(String filename) throws IOException {
        log.debug("Retrieving file: {}", filename);

        // Validation sécurité
        if (!FileValidationUtil.isSafeFilename(filename)) {
            throw new SecurityException("Invalid filename: " + filename);
        }

        return fileStorageService.loadFile(filename);
    }

    /**
     * Supprime un fichier
     */
    public void deleteFile(String filename) throws IOException {
        log.info("Deleting file: {}", filename);
        fileStorageService.deleteFile(filename);
    }

    /**
     * Upload avec rollback automatique
     * Utilise pour des opérations transactionnelles
     */
    public FileUploadResult uploadWithRollback(MultipartFile file) {
        String savedFileName = null;
        try {
            savedFileName = uploadFile(file);
            return FileUploadResult.success(savedFileName);
        } catch (Exception e) {
            // Cleanup en cas d'erreur
            if (savedFileName != null) {
                try {
                    deleteFile(savedFileName);
                    log.info("Rollback successful: deleted {}", savedFileName);
                } catch (IOException deleteEx) {
                    log.error("Failed to rollback file: {}", savedFileName, deleteEx);
                }
            }
            return FileUploadResult.failure(e.getMessage());
        }
    }

    @Value
    @Builder
    public static class FileUploadResult {
        boolean success;
        String filename;
        String errorMessage;

        public static FileUploadResult success(String filename) {
            return FileUploadResult.builder()
                .success(true)
                .filename(filename)
                .build();
        }

        public static FileUploadResult failure(String errorMessage) {
            return FileUploadResult.builder()
                .success(false)
                .errorMessage(errorMessage)
                .build();
        }
    }
}
```

**StudentController Refactoré :**
```java
// api/rest/student/StudentController.java
@RestController
@RequestMapping("/api/students")
@RequiredArgsConstructor
public class StudentController {

    private final StudentService studentService;
    private final FileManagementService fileManagementService;  // INJECTION DU SERVICE
    private final StudentMapper studentMapper;

    @PostMapping("/createStudent")
    public ResponseEntity<StudentResponse> createStudent(
            @Valid @ModelAttribute CreateStudentRequest request,
            @RequestParam("file") MultipartFile file) {

        // 1. Upload du fichier via le service
        FileManagementService.FileUploadResult uploadResult =
            fileManagementService.uploadWithRollback(file);

        if (!uploadResult.isSuccess()) {
            return ResponseEntity
                .badRequest()
                .body(StudentResponse.error(uploadResult.getErrorMessage()));
        }

        // 2. Créer l'étudiant avec le nom du fichier
        request.setPhotoFilename(uploadResult.getFilename());
        StudentDTO dto = studentService.createStudent(request);

        // 3. Retourner la réponse
        return ResponseEntity.ok(StudentResponse.success(dto));
    }

    @GetMapping("/photos/{fileName}")
    public ResponseEntity<Resource> getPhoto(@PathVariable String fileName) {
        try {
            Resource resource = fileManagementService.getFile(fileName);
            MediaType mediaType = MediaTypeFactory
                .getMediaType(fileName)
                .orElse(MediaType.APPLICATION_OCTET_STREAM);

            return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .body(resource);

        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        } catch (SecurityException e) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
    }
}
```

**Impact :**
- ✅ Controller ne contient que du routing
- ✅ Logique métier dans le service
- ✅ Réutilisable par TeacherController
- ✅ Testable unitairement

---

### 1.4 Refactorer PaymentController (Supprimer accès direct aux repos)

**Problème Actuel :**
```java
// PaymentController.java - ANTI-PATTERN
@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService paymentService;

    // 6 REPOSITORIES DIRECTEMENT INJECTÉS - VIOLATION DIP
    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final SessionRepository sessionRepository;
    private final SessionMapper sessionMapper;

    @GetMapping("/student/{studentId}")
    public ResponseEntity<List<PaymentDTO>> getPaymentsByStudent(@PathVariable Long studentId) {
        // ACCÈS DIRECT AU REPOSITORY DANS LE CONTROLLER
        List<PaymentEntity> payments = paymentRepository.findByStudentId(studentId);

        // CONVERSION MANUELLE au lieu d'utiliser mapper
        return ResponseEntity.ok(payments.stream()
            .map(payment -> {
                PaymentDTO dto = new PaymentDTO();
                dto.setId(payment.getId());
                // ... 20 lignes de mapping manuel
                return dto;
            })
            .toList());
    }
}
```

**Solution Refactorée :**

```java
// api/rest/payment/PaymentController.java
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {

    // SEULEMENT LE SERVICE - Pas de repositories !
    private final PaymentService paymentService;
    private final PaymentMapper paymentMapper;  // Pour conversion DTO

    /**
     * Récupère tous les paiements d'un étudiant
     */
    @GetMapping("/student/{studentId}")
    public ResponseEntity<PageResponse<PaymentResponse>> getPaymentsByStudent(
            @PathVariable Long studentId,
            @PageableDefault(size = 20) Pageable pageable) {

        log.info("Fetching payments for student: {}", studentId);

        // DÉLÉGUER AU SERVICE
        Page<PaymentDTO> payments = paymentService.findByStudentId(studentId, pageable);

        // UTILISER LE MAPPER
        Page<PaymentResponse> response = payments.map(paymentMapper::toResponse);

        return ResponseEntity.ok(PageResponse.of(response));
    }

    /**
     * Traiter un paiement pour une série
     */
    @PostMapping("/process")
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody ProcessPaymentRequest request) {

        log.info("Processing payment for student: {}, group: {}, series: {}",
            request.getStudentId(), request.getGroupId(), request.getSessionSeriesId());

        // VALIDATION AU NIVEAU CONTROLLER
        if (request.getAmount() <= 0) {
            throw new ValidationException("Amount must be positive");
        }

        // DÉLÉGUER AU SERVICE
        PaymentDTO payment = paymentService.processPayment(
            request.getStudentId(),
            request.getGroupId(),
            request.getSessionSeriesId(),
            request.getAmount()
        );

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(paymentMapper.toResponse(payment));
    }

    /**
     * Récupérer le statut des paiements pour un groupe
     */
    @GetMapping("/status/group/{groupId}")
    public ResponseEntity<List<StudentPaymentStatusResponse>> getGroupPaymentStatus(
            @PathVariable Long groupId) {

        log.info("Fetching payment status for group: {}", groupId);

        List<StudentPaymentStatusDTO> statuses =
            paymentService.getPaymentStatusForGroup(groupId);

        return ResponseEntity.ok(
            statuses.stream()
                .map(paymentMapper::toStatusResponse)
                .toList()
        );
    }
}
```

**Modifications dans PaymentService :**

```java
// application/service/payment/PaymentService.java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final SessionRepository sessionRepository;

    private final PaymentMapper paymentMapper;

    /**
     * Trouve les paiements par étudiant (PAGINÉ)
     * NOUVELLE MÉTHODE déplacée depuis le controller
     */
    @Transactional(readOnly = true)
    public Page<PaymentDTO> findByStudentId(Long studentId, Pageable pageable) {
        log.debug("Finding payments for student: {}", studentId);

        // Vérifier que l'étudiant existe
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student", studentId);
        }

        // Récupérer avec pagination
        Page<Payment> payments = paymentRepository.findByStudentId(studentId, pageable);

        // Mapper via MapStruct
        return payments.map(paymentMapper::toDTO);
    }

    /**
     * Traiter un paiement (TRANSACTIONAL)
     */
    @Transactional  // ATOMIC
    public PaymentDTO processPayment(Long studentId, Long groupId,
                                     Long sessionSeriesId, double amountPaid) {
        // Logique existante...
        // Retourne DTO via mapper
        Payment savedPayment = paymentRepository.save(payment);
        return paymentMapper.toDTO(savedPayment);
    }
}
```

**Impact :**
- ✅ Controller respecte le principe SRP (Single Responsibility)
- ✅ Pas d'accès direct aux repositories
- ✅ Utilisation cohérente des mappers
- ✅ Pagination automatique
- ✅ Testabilité améliorée

---

## 📋 Phase 2 : Restructuration Services (Semaine 3-4)

### 2.1 Diviser PaymentService (496 LOC → 3 services)

**Architecture Cible :**

```
payment/
├── PaymentService.java              [CRUD de base - 150 LOC]
├── PaymentProcessingService.java    [Traitement - 200 LOC]
├── PaymentStatusService.java        [Calcul statut - 100 LOC]
└── PaymentDistributionService.java  [Distribution - 100 LOC]
```

**PaymentService (CRUD uniquement) :**
```java
// application/service/payment/PaymentService.java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final PaymentMapper paymentMapper;

    /**
     * Crée un paiement (sans traitement de distribution)
     */
    @Transactional
    public PaymentDTO createPayment(CreatePaymentRequest request) {
        log.info("Creating payment for student: {}", request.getStudentId());

        Student student = studentRepository.findById(request.getStudentId())
            .orElseThrow(() -> new ResourceNotFoundException("Student", request.getStudentId()));

        Payment payment = Payment.builder()
            .student(student)
            .amount(Money.of(request.getAmount()))
            .paymentDate(LocalDateTime.now())
            .build();

        Payment saved = paymentRepository.save(payment);
        return paymentMapper.toDTO(saved);
    }

    /**
     * Récupère un paiement par ID
     */
    @Transactional(readOnly = true)
    public PaymentDTO findById(Long id) {
        Payment payment = paymentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", id));
        return paymentMapper.toDTO(payment);
    }

    /**
     * Récupère les paiements d'un étudiant
     */
    @Transactional(readOnly = true)
    public Page<PaymentDTO> findByStudentId(Long studentId, Pageable pageable) {
        if (!studentRepository.existsById(studentId)) {
            throw new ResourceNotFoundException("Student", studentId);
        }

        Page<Payment> payments = paymentRepository.findByStudentId(studentId, pageable);
        return payments.map(paymentMapper::toDTO);
    }

    /**
     * Supprime un paiement
     */
    @Transactional
    public void deletePayment(Long id) {
        if (!paymentRepository.existsById(id)) {
            throw new ResourceNotFoundException("Payment", id);
        }
        paymentRepository.deleteById(id);
        log.info("Payment deleted: {}", id);
    }
}
```

**PaymentProcessingService (Logique de traitement) :**
```java
// application/service/payment/PaymentProcessingService.java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentProcessingService {

    private final PaymentRepository paymentRepository;
    private final StudentRepository studentRepository;
    private final GroupRepository groupRepository;
    private final SessionSeriesRepository sessionSeriesRepository;

    private final PaymentDistributionService distributionService;
    private final PaymentMapper paymentMapper;

    /**
     * Traite un paiement pour une série complète
     *
     * @return le paiement créé avec ses détails
     */
    @Transactional
    public PaymentDTO processSeriesPayment(ProcessPaymentRequest request) {
        log.info("Processing series payment: student={}, group={}, series={}, amount={}",
            request.getStudentId(), request.getGroupId(),
            request.getSessionSeriesId(), request.getAmount());

        // 1. Valider les entités
        Student student = findStudent(request.getStudentId());
        Group group = findGroup(request.getGroupId());
        SessionSeries series = findSessionSeries(request.getSessionSeriesId());

        // 2. Vérifier que l'étudiant est dans le groupe
        validateStudentInGroup(student, group);

        // 3. Créer le paiement
        Payment payment = Payment.builder()
            .student(student)
            .group(group)
            .sessionSeries(series)
            .amount(Money.of(request.getAmount()))
            .paymentDate(LocalDateTime.now())
            .paymentType(PaymentType.SERIES)
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 4. Distribuer le paiement sur les sessions
        distributionService.distributePaymentToSessions(savedPayment);

        log.info("Series payment processed successfully: paymentId={}", savedPayment.getId());

        return paymentMapper.toDTO(savedPayment);
    }

    /**
     * Traite un paiement de rattrapage pour une session unique
     */
    @Transactional
    public PaymentDTO processCatchUpPayment(CatchUpPaymentRequest request) {
        log.info("Processing catch-up payment: student={}, session={}, amount={}",
            request.getStudentId(), request.getSessionId(), request.getAmount());

        // 1. Valider
        Student student = findStudent(request.getStudentId());
        Session session = findSession(request.getSessionId());

        // 2. Vérifier que l'étudiant a assisté
        validateStudentAttendance(student, session);

        // 3. Créer le paiement
        Payment payment = Payment.builder()
            .student(student)
            .session(session)
            .amount(Money.of(request.getAmount()))
            .paymentDate(LocalDateTime.now())
            .paymentType(PaymentType.CATCH_UP)
            .build();

        Payment savedPayment = paymentRepository.save(payment);

        // 4. Créer le détail de paiement
        PaymentDetail detail = PaymentDetail.builder()
            .payment(savedPayment)
            .session(session)
            .amount(Money.of(request.getAmount()))
            .build();

        paymentDetailRepository.save(detail);

        log.info("Catch-up payment processed: paymentId={}", savedPayment.getId());

        return paymentMapper.toDTO(savedPayment);
    }

    // Méthodes privées de validation
    private Student findStudent(Long id) {
        return studentRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Student", id));
    }

    private Group findGroup(Long id) {
        return groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Group", id));
    }

    private SessionSeries findSessionSeries(Long id) {
        return sessionSeriesRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("SessionSeries", id));
    }

    private void validateStudentInGroup(Student student, Group group) {
        if (!student.getGroups().contains(group)) {
            throw new ValidationException(
                String.format("Student %d is not enrolled in group %d",
                    student.getId(), group.getId())
            );
        }
    }
}
```

**PaymentDistributionService (Logique de distribution) :**
```java
// application/service/payment/PaymentDistributionService.java
@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class PaymentDistributionService {

    private final SessionRepository sessionRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final AttendanceRepository attendanceRepository;

    /**
     * Distribue un paiement sur toutes les sessions d'une série
     * en fonction de la présence de l'étudiant
     *
     * @param payment le paiement à distribuer
     */
    @Transactional
    public void distributePaymentToSessions(Payment payment) {
        log.info("Distributing payment {} to sessions", payment.getId());

        // 1. Récupérer toutes les sessions de la série
        List<Session> sessions = sessionRepository
            .findBySessionSeriesId(payment.getSessionSeries().getId());

        if (sessions.isEmpty()) {
            log.warn("No sessions found for series: {}", payment.getSessionSeries().getId());
            return;
        }

        // 2. Filtrer les sessions où l'étudiant était présent
        List<Session> attendedSessions = sessions.stream()
            .filter(session -> wasStudentPresent(payment.getStudent(), session))
            .toList();

        if (attendedSessions.isEmpty()) {
            log.warn("Student {} attended no sessions in series {}",
                payment.getStudent().getId(), payment.getSessionSeries().getId());
            return;
        }

        // 3. Calculer le montant par session
        Money totalAmount = payment.getAmount();
        Money amountPerSession = totalAmount.divide(attendedSessions.size());

        // 4. Créer les détails de paiement
        List<PaymentDetail> details = attendedSessions.stream()
            .map(session -> PaymentDetail.builder()
                .payment(payment)
                .session(session)
                .amount(amountPerSession)
                .build())
            .toList();

        paymentDetailRepository.saveAll(details);

        log.info("Payment {} distributed to {} sessions",
            payment.getId(), attendedSessions.size());
    }

    /**
     * Vérifie si un étudiant était présent à une session
     */
    private boolean wasStudentPresent(Student student, Session session) {
        return attendanceRepository
            .findByStudentIdAndSessionId(student.getId(), session.getId())
            .map(Attendance::isPresent)
            .orElse(false);
    }

    /**
     * Recalcule la distribution pour un paiement existant
     * Utile si les présences changent après le paiement
     */
    @Transactional
    public void recalculateDistribution(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
            .orElseThrow(() -> new ResourceNotFoundException("Payment", paymentId));

        // Supprimer les anciens détails
        paymentDetailRepository.deleteByPaymentId(paymentId);

        // Redistribuer
        distributePaymentToSessions(payment);

        log.info("Payment {} redistributed", paymentId);
    }
}
```

**PaymentStatusService (Calcul des statuts) :**
```java
// application/service/payment/PaymentStatusService.java
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@Slf4j
public class PaymentStatusService {

    private final PaymentRepository paymentRepository;
    private final PaymentDetailRepository paymentDetailRepository;
    private final SessionRepository sessionRepository;
    private final StudentRepository studentRepository;

    /**
     * Calcule le statut de paiement pour un groupe complet
     * Retourne le statut de chaque étudiant
     */
    public List<StudentPaymentStatus> getGroupPaymentStatus(Long groupId) {
        log.info("Calculating payment status for group: {}", groupId);

        // 1. Récupérer tous les étudiants du groupe
        List<Student> students = studentRepository.findByGroupsId(groupId);

        // 2. Calculer le statut de chaque étudiant
        return students.stream()
            .map(student -> calculateStudentStatus(student, groupId))
            .toList();
    }

    /**
     * Calcule le statut de paiement pour un étudiant dans un groupe
     */
    public StudentPaymentStatus getStudentPaymentStatus(Long studentId, Long groupId) {
        Student student = studentRepository.findById(studentId)
            .orElseThrow(() -> new ResourceNotFoundException("Student", studentId));

        return calculateStudentStatus(student, groupId);
    }

    /**
     * Calcule les détails du statut de paiement pour un étudiant
     */
    private StudentPaymentStatus calculateStudentStatus(Student student, Long groupId) {
        // 1. Trouver toutes les séries du groupe
        List<SessionSeries> series = sessionSeriesRepository.findByGroupId(groupId);

        // 2. Pour chaque série, calculer le statut
        List<SeriesPaymentStatus> seriesStatuses = series.stream()
            .map(s -> calculateSeriesStatus(student, s))
            .toList();

        // 3. Agréger les montants
        Money totalExpected = seriesStatuses.stream()
            .map(SeriesPaymentStatus::getExpectedAmount)
            .reduce(Money.ZERO, Money::add);

        Money totalPaid = seriesStatuses.stream()
            .map(SeriesPaymentStatus::getPaidAmount)
            .reduce(Money.ZERO, Money::add);

        Money remaining = totalExpected.subtract(totalPaid);

        return StudentPaymentStatus.builder()
            .studentId(student.getId())
            .studentName(student.getFullName())
            .groupId(groupId)
            .totalExpectedAmount(totalExpected)
            .totalPaidAmount(totalPaid)
            .remainingAmount(remaining)
            .seriesStatuses(seriesStatuses)
            .isPaidInFull(remaining.isZero())
            .build();
    }

    /**
     * Calcule le statut pour une série spécifique
     */
    private SeriesPaymentStatus calculateSeriesStatus(Student student, SessionSeries series) {
        // 1. Montant attendu basé sur le pricing et le nombre de sessions
        int sessionCount = sessionRepository.countBySessionSeriesId(series.getId());
        Money expectedAmount = series.getPricing().getAmount().multiply(sessionCount);

        // 2. Montant payé
        Money paidAmount = paymentRepository
            .findAmountPaidForStudentAndSeries(student.getId(), series.getId())
            .orElse(Money.ZERO);

        // 3. Détails des sessions
        List<SessionPaymentStatus> sessionStatuses =
            calculateSessionStatuses(student, series);

        return SeriesPaymentStatus.builder()
            .seriesId(series.getId())
            .seriesName(series.getName())
            .expectedAmount(expectedAmount)
            .paidAmount(paidAmount)
            .remainingAmount(expectedAmount.subtract(paidAmount))
            .sessionStatuses(sessionStatuses)
            .build();
    }

    private List<SessionPaymentStatus> calculateSessionStatuses(
            Student student, SessionSeries series) {

        List<Session> sessions = sessionRepository.findBySessionSeriesId(series.getId());

        return sessions.stream()
            .map(session -> {
                Money paidForSession = paymentDetailRepository
                    .findAmountPaidForSession(student.getId(), session.getId())
                    .orElse(Money.ZERO());

                boolean attended = attendanceRepository
                    .findByStudentIdAndSessionId(student.getId(), session.getId())
                    .map(Attendance::isPresent)
                    .orElse(false);

                return SessionPaymentStatus.builder()
                    .sessionId(session.getId())
                    .sessionDate(session.getDate())
                    .paidAmount(paidForSession)
                    .attended(attended)
                    .build();
            })
            .toList();
    }
}
```

**Impact du Découpage :**
- ✅ PaymentService : 150 LOC (CRUD simple)
- ✅ PaymentProcessingService : 200 LOC (Logique métier)
- ✅ PaymentDistributionService : 100 LOC (Distribution)
- ✅ PaymentStatusService : 100 LOC (Calculs)
- ✅ Total : 550 LOC mais **4 classes testables indépendamment**
- ✅ Chaque classe a une **responsabilité unique**
- ✅ **Facilité de maintenance** et d'évolution

---

### 2.2 Implémenter Pagination Globale

**Créer Configuration Centralisée :**

```java
// infrastructure/config/web/PaginationConfig.java
package com.school.management.infrastructure.config.web;

@Configuration
public class PaginationConfig {

    /**
     * Configuration par défaut de la pagination
     */
    @Bean
    public PageableHandlerMethodArgumentResolverCustomizer customizer() {
        return pageableResolver -> {
            pageableResolver.setFallbackPageable(PageRequest.of(0, 20));
            pageableResolver.setMaxPageSize(100);
            pageableResolver.setPageParameterName("page");
            pageableResolver.setSizeParameterName("size");
            pageableResolver.setOneIndexedParameters(false);
        };
    }
}
```

**Wrapper de Réponse Paginée :**

```java
// api/response/common/PageResponse.java
package com.school.management.api.response.common;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    private List<T> content;
    private PageMetadata metadata;

    @Data
    @Builder
    public static class PageMetadata {
        private int page;
        private int size;
        private long totalElements;
        private int totalPages;
        private boolean first;
        private boolean last;
        private boolean empty;
    }

    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .metadata(PageMetadata.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .build())
            .build();
    }
}
```

**Exemple d'Utilisation dans Controller :**

```java
// api/rest/student/StudentController.java
@GetMapping
public ResponseEntity<PageResponse<StudentResponse>> getAllStudents(
        @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

    Page<StudentDTO> students = studentService.findAll(pageable);
    Page<StudentResponse> response = students.map(studentMapper::toResponse);

    return ResponseEntity.ok(PageResponse.of(response));
}

// Exemple d'appel:
// GET /api/students?page=0&size=20&sort=lastName,asc
```

---

## 📋 Phase 3 : Améliorations Production (Semaine 5-6)

### 3.1 Implémenter Authentication & Authorization

**JWT Configuration :**

```java
// infrastructure/config/security/JwtProperties.java
@Configuration
@ConfigurationProperties(prefix = "jwt")
@Data
public class JwtProperties {
    private String secret;
    private long expirationMs = 86400000; // 24 heures
    private String issuer = "school-management";
}
```

**JWT Token Provider :**

```java
// infrastructure/config/security/JwtTokenProvider.java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * Génère un token JWT pour un utilisateur
     */
    public String generateToken(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtProperties.getExpirationMs());

        return Jwts.builder()
            .setSubject(userDetails.getUsername())
            .setIssuedAt(now)
            .setExpiration(expiryDate)
            .setIssuer(jwtProperties.getIssuer())
            .signWith(SignatureAlgorithm.HS512, jwtProperties.getSecret())
            .compact();
    }

    /**
     * Valide un token JWT
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(jwtProperties.getSecret())
                .parseClaimsJws(token);
            return true;
        } catch (SignatureException ex) {
            log.error("Invalid JWT signature");
        } catch (MalformedJwtException ex) {
            log.error("Invalid JWT token");
        } catch (ExpiredJwtException ex) {
            log.error("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            log.error("Unsupported JWT token");
        } catch (IllegalArgumentException ex) {
            log.error("JWT claims string is empty");
        }
        return false;
    }

    /**
     * Extrait le username depuis le token
     */
    public String getUsernameFromToken(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(jwtProperties.getSecret())
            .parseClaimsJws(token)
            .getBody();

        return claims.getSubject();
    }
}
```

**JWT Authentication Filter :**

```java
// infrastructure/config/security/JwtAuthenticationFilter.java
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider tokenProvider;
    private final UserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                  HttpServletResponse response,
                                  FilterChain filterChain) throws ServletException, IOException {

        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && tokenProvider.validateToken(jwt)) {
                String username = tokenProvider.getUsernameFromToken(jwt);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());

                authentication.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.debug("Set authentication for user: {}", username);
            }
        } catch (Exception ex) {
            log.error("Could not set user authentication in security context", ex);
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

**Security Configuration Complète :**

```java
// infrastructure/config/security/SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CorsConfig corsConfig;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfig.corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Public endpoints
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/api/public/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/actuator/health").permitAll()

                // Admin endpoints
                .requestMatchers("/api/admin/**").hasRole("ADMIN")

                // Teacher endpoints
                .requestMatchers(HttpMethod.POST, "/api/groups/**").hasAnyRole("ADMIN", "TEACHER")
                .requestMatchers(HttpMethod.PUT, "/api/groups/**").hasAnyRole("ADMIN", "TEACHER")

                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setContentType("application/json");
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.getWriter().write(
                        "{\"error\": \"Unauthorized\", \"message\": \"" +
                        authException.getMessage() + "\"}");
                })
            );

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration authenticationConfiguration) throws Exception {
        return authenticationConfiguration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

**CORS Configuration Externalisée :**

```java
// infrastructure/config/security/CorsConfig.java
@Configuration
@ConfigurationProperties(prefix = "cors")
@Data
public class CorsConfig {

    private List<String> allowedOrigins = List.of("http://localhost:4200");
    private List<String> allowedMethods = List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS");
    private List<String> allowedHeaders = List.of("*");
    private long maxAge = 3600;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(allowedMethods);
        configuration.setAllowedHeaders(allowedHeaders);
        configuration.setAllowCredentials(true);
        configuration.setMaxAge(maxAge);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
```

**Configuration dans application.properties :**

```properties
# JWT Configuration
jwt.secret=${JWT_SECRET:your-256-bit-secret-key-for-dev-only-change-in-prod}
jwt.expiration-ms=86400000
jwt.issuer=school-management-api

# CORS Configuration
cors.allowed-origins=${CORS_ORIGINS:http://localhost:4200,http://localhost:3000}
cors.allowed-methods=GET,POST,PUT,DELETE,PATCH,OPTIONS
cors.allowed-headers=*
cors.max-age=3600
```

---

### 3.2 Gestion Centralisée des Erreurs

**Hiérarchie d'Exceptions :**

```java
// shared/exception/BusinessException.java
public abstract class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Map<String, Object> details;

    protected BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    protected BusinessException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.details = new HashMap<>();
    }

    public BusinessException addDetail(String key, Object value) {
        this.details.put(key, value);
        return this;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public Map<String, Object> getDetails() {
        return Collections.unmodifiableMap(details);
    }
}

// shared/exception/ResourceNotFoundException.java
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceType, Object id) {
        super(
            ErrorCode.RESOURCE_NOT_FOUND,
            String.format("%s not found with id: %s", resourceType, id)
        );
        addDetail("resourceType", resourceType);
        addDetail("id", id);
    }
}

// shared/exception/ValidationException.java
public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
    }

    public ValidationException(String field, String message) {
        super(ErrorCode.VALIDATION_ERROR, message);
        addDetail("field", field);
    }
}
```

**ErrorCode Enum :**

```java
// shared/constant/ErrorCode.java
@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // Generic errors (1xxx)
    INTERNAL_SERVER_ERROR("ERR-1000", "Internal server error occurred"),
    RESOURCE_NOT_FOUND("ERR-1001", "Resource not found"),
    VALIDATION_ERROR("ERR-1002", "Validation failed"),
    UNAUTHORIZED("ERR-1003", "Unauthorized access"),
    FORBIDDEN("ERR-1004", "Access forbidden"),

    // Student errors (2xxx)
    STUDENT_NOT_FOUND("ERR-2001", "Student not found"),
    STUDENT_ALREADY_EXISTS("ERR-2002", "Student already exists"),
    STUDENT_NOT_IN_GROUP("ERR-2003", "Student not enrolled in group"),

    // Payment errors (3xxx)
    PAYMENT_NOT_FOUND("ERR-3001", "Payment not found"),
    INVALID_PAYMENT_AMOUNT("ERR-3002", "Invalid payment amount"),
    PAYMENT_ALREADY_PROCESSED("ERR-3003", "Payment already processed"),
    INSUFFICIENT_PAYMENT("ERR-3004", "Payment amount insufficient"),

    // Group errors (4xxx)
    GROUP_NOT_FOUND("ERR-4001", "Group not found"),
    GROUP_ALREADY_ASSOCIATED("ERR-4002", "Student already in group"),
    GROUP_CAPACITY_EXCEEDED("ERR-4003", "Group capacity exceeded"),

    // File errors (5xxx)
    FILE_UPLOAD_ERROR("ERR-5001", "File upload failed"),
    FILE_NOT_FOUND("ERR-5002", "File not found"),
    INVALID_FILE_TYPE("ERR-5003", "Invalid file type"),
    FILE_TOO_LARGE("ERR-5004", "File size exceeds limit");

    private final String code;
    private final String message;
}
```

**Global Exception Handler Amélioré :**

```java
// shared/exception/GlobalExceptionHandler.java
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Gère les exceptions métier personnalisées
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(BusinessException ex) {
        log.warn("Business exception: {} - {}", ex.getErrorCode().getCode(), ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .details(ex.getDetails())
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Gère ResourceNotFoundException
     */
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleResourceNotFoundException(ResourceNotFoundException ex) {
        log.warn("Resource not found: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.NOT_FOUND.value())
            .error(HttpStatus.NOT_FOUND.getReasonPhrase())
            .code(ex.getErrorCode().getCode())
            .message(ex.getMessage())
            .details(ex.getDetails())
            .build();

        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error);
    }

    /**
     * Gère les erreurs de validation (@Valid)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());

        Map<String, String> fieldErrors = ex.getBindingResult()
            .getFieldErrors()
            .stream()
            .collect(Collectors.toMap(
                FieldError::getField,
                error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value",
                (existing, replacement) -> existing
            ));

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.BAD_REQUEST.value())
            .error("Validation Failed")
            .code(ErrorCode.VALIDATION_ERROR.getCode())
            .message("Input validation failed")
            .details(Map.of("fieldErrors", fieldErrors))
            .build();

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error);
    }

    /**
     * Gère les violations de contraintes de base de données
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErrorResponse> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation", ex);

        String message = "Database constraint violation";
        if (ex.getRootCause() instanceof ConstraintViolationException) {
            message = "Unique constraint or foreign key violation";
        }

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.CONFLICT.value())
            .error(HttpStatus.CONFLICT.getReasonPhrase())
            .code("ERR-DB-001")
            .message(message)
            .build();

        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    /**
     * Gère les erreurs non autorisées (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.FORBIDDEN.value())
            .error(HttpStatus.FORBIDDEN.getReasonPhrase())
            .code(ErrorCode.FORBIDDEN.getCode())
            .message("You don't have permission to access this resource")
            .build();

        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(error);
    }

    /**
     * Gère toutes les autres exceptions non capturées
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        log.error("Unexpected error occurred", ex);

        ErrorResponse error = ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
            .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
            .code(ErrorCode.INTERNAL_SERVER_ERROR.getCode())
            .message("An unexpected error occurred. Please contact support.")
            .build();

        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
```

**ErrorResponse DTO :**

```java
// api/response/common/ErrorResponse.java
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime timestamp;

    private int status;
    private String error;
    private String code;
    private String message;
    private String path;
    private Map<String, Object> details;

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return ErrorResponse.builder()
            .timestamp(LocalDateTime.now())
            .code(errorCode.getCode())
            .message(message)
            .build();
    }
}
```

---

### 3.3 Logging Centralisé

**AOP Logging Aspect :**

```java
// infrastructure/config/logging/LoggingAspect.java
@Aspect
@Component
@Slf4j
public class LoggingAspect {

    /**
     * Log toutes les méthodes publiques des services
     */
    @Around("execution(* com.school.management.application.service..*(..))")
    public Object logServiceMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();

        log.info("Entering {}.{}() with args: {}", className, methodName,
            Arrays.toString(args));

        long start = System.currentTimeMillis();

        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - start;

            log.info("Exiting {}.{}() with result: {} ({}ms)",
                className, methodName, result, executionTime);

            return result;
        } catch (Exception e) {
            log.error("Exception in {}.{}(): {}", className, methodName, e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Log toutes les requêtes HTTP vers les controllers
     */
    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object logControllerMethods(ProceedingJoinPoint joinPoint) throws Throwable {
        ServletRequestAttributes attributes =
            (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();

            log.info("HTTP {} {} from {}",
                request.getMethod(),
                request.getRequestURI(),
                request.getRemoteAddr());
        }

        return joinPoint.proceed();
    }
}
```

---

## 🎯 Résumé du Plan

### Timeline

| Phase | Durée | Priorité | Effort |
|-------|-------|----------|--------|
| **Phase 1 : Corrections Critiques** | 2 semaines | P0 | 80h |
| **Phase 2 : Restructuration Services** | 2 semaines | P1 | 60h |
| **Phase 3 : Production-Ready** | 2 semaines | P2 | 60h |
| **Total** | **6 semaines** | - | **200h** |

### Priorités

**À faire IMMÉDIATEMENT (Phase 1) :**
1. ✅ Éliminer ApplicationContextProvider
2. ✅ Ajouter @Transactional sur PaymentService.processPayment()
3. ✅ Extraire FileManagementService
4. ✅ Refactorer PaymentController

**Après Phase 1 (Phase 2) :**
5. Diviser PaymentService en 4 services
6. Implémenter pagination globale
7. Créer DTOs Request/Response séparés

**Avant Production (Phase 3) :**
8. Implémenter JWT Authentication
9. Améliorer gestion des erreurs
10. Ajouter logging centralisé

---

**Document créé le** : 2025-12-04
**Version** : 1.0
**Auteur** : Claude Code
