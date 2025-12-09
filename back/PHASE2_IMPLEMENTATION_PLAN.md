# 📐 Phase 2 - Plan d'Implémentation

**Date de début** : 2025-12-04
**Phase** : 2 - Restructuration Services
**Statut** : 🚀 EN COURS

---

## 🎯 Objectifs Phase 2

Phase 2 se concentre sur la **restructuration des services** et l'**amélioration de l'architecture** pour préparer l'application à la production. Les objectifs principaux sont :

1. **Diviser les services monolithiques** - PaymentService (496 LOC) → 4 services spécialisés
2. **Créer des Value Objects** - Money, Email, PhoneNumber pour une meilleure encapsulation
3. **Implémenter la pagination globale** - Sur tous les endpoints de liste
4. **Séparer Request/Response DTOs** - Améliorer la clarté de l'API

---

## 📊 État Initial

### Services à Refactorer

| Service | LOC Actuel | Responsabilités | Problèmes |
|---------|------------|-----------------|-----------|
| PaymentService | 496 | CRUD + Processing + Status + Distribution | Trop de responsabilités, difficile à tester |
| StudentService | ~300 | CRUD + Search + History | Acceptable mais pourrait être optimisé |
| GroupService | ~250 | CRUD + Management | Acceptable |

### Endpoints Sans Pagination

- `GET /api/students` - Retourne TOUS les étudiants (risque de surcharge)
- `GET /api/groups` - Retourne TOUS les groupes
- `GET /api/payments` - Retourne TOUS les paiements
- `GET /api/teachers` - Retourne TOUS les professeurs
- `GET /api/sessions` - Retourne TOUTES les sessions

### DTOs Mixtes (Request + Response)

Actuellement, les mêmes DTOs sont utilisés pour les requêtes ET les réponses :
- `StudentDTO` - Utilisé pour créer ET retourner un étudiant
- `PaymentDTO` - Utilisé pour traiter ET retourner un paiement
- `GroupDTO` - Utilisé pour créer ET retourner un groupe

---

## 🏗️ Architecture Cible Phase 2

### 1. Structure des Services Payment

```
application/service/payment/
├── PaymentService.java                    [CRUD - 150 LOC]
│   ├── createPayment()
│   ├── findById()
│   ├── findByStudentId() - PAGINÉ
│   └── deletePayment()
│
├── PaymentProcessingService.java          [Traitement - 200 LOC]
│   ├── processSeriesPayment()
│   ├── processCatchUpPayment()
│   └── validatePaymentRequest()
│
├── PaymentDistributionService.java        [Distribution - 100 LOC]
│   ├── distributePaymentToSessions()
│   ├── recalculateDistribution()
│   └── wasStudentPresent()
│
└── PaymentStatusService.java              [Statut - 100 LOC]
    ├── getGroupPaymentStatus()
    ├── getStudentPaymentStatus()
    └── calculateSeriesStatus()
```

### 2. Value Objects

```
domain/valueobject/
├── Money.java              [Encapsulation montants + opérations]
├── Email.java              [Validation email + formatage]
├── PhoneNumber.java        [Validation + formatage téléphone]
└── DateRange.java          [Validation plages de dates]
```

### 3. Pagination

```
infrastructure/config/web/
└── PaginationConfig.java   [Configuration centralisée]

api/response/common/
└── PageResponse.java       [Wrapper générique pour pages]
```

### 4. Request/Response DTOs

```
api/request/
├── student/
│   ├── CreateStudentRequest.java
│   └── UpdateStudentRequest.java
├── payment/
│   ├── ProcessPaymentRequest.java
│   └── CatchUpPaymentRequest.java
└── group/
    ├── CreateGroupRequest.java
    └── UpdateGroupRequest.java

api/response/
├── student/
│   └── StudentResponse.java
├── payment/
│   ├── PaymentResponse.java
│   └── PaymentStatusResponse.java
└── group/
    └── GroupResponse.java
```

---

## 📋 Plan d'Implémentation

### Étape 1 : Value Objects (Priorité HAUTE)

**Ordre de création** :
1. ✅ Money.java - Base pour les montants de paiement
2. Email.java - Validation des emails
3. PhoneNumber.java - Validation des téléphones
4. DateRange.java - Validation des plages de dates

**Impact** :
- PaymentService, PaymentEntity, PricingEntity utilisent Money
- StudentEntity, TeacherEntity utilisent Email et PhoneNumber
- SessionSearchCriteria utilise DateRange

---

### Étape 2 : Diviser PaymentService (Priorité HAUTE)

**Ordre de création** :
1. ✅ PaymentService - CRUD de base uniquement
2. ✅ PaymentDistributionService - Logique de distribution
3. ✅ PaymentProcessingService - Traitement des paiements (utilise DistributionService)
4. ✅ PaymentStatusService - Calcul des statuts

**Dépendances** :
```
PaymentController
    ↓
PaymentProcessingService → PaymentService (CRUD)
    ↓                      → PaymentDistributionService
    ↓
PaymentStatusService → PaymentService (CRUD)
```

---

### Étape 3 : Pagination Globale (Priorité MOYENNE)

**Ordre de création** :
1. ✅ PaginationConfig.java - Configuration Spring
2. ✅ PageResponse.java - Wrapper générique
3. ✅ Mettre à jour StudentService.findAll() → Page<>
4. ✅ Mettre à jour GroupService.findAll() → Page<>
5. ✅ Mettre à jour PaymentService.findAll() → Page<>
6. ✅ Mettre à jour TeacherService.findAll() → Page<>
7. ✅ Mettre à jour SessionService.findAll() → Page<>

**Exemple de signature** :
```java
// AVANT
List<StudentDTO> findAll();

// APRÈS
Page<StudentDTO> findAll(Pageable pageable);
```

---

### Étape 4 : Séparer Request/Response DTOs (Priorité BASSE)

**Ordre de création** :
1. Créer Request DTOs pour les endpoints POST/PUT
2. Créer Response DTOs avec uniquement les champs nécessaires
3. Mettre à jour les mappers
4. Mettre à jour les controllers

**Exemple** :
```java
// AVANT - Même DTO pour tout
@PostMapping
public ResponseEntity<StudentDTO> create(@RequestBody StudentDTO dto) { ... }

// APRÈS - DTOs séparés
@PostMapping
public ResponseEntity<StudentResponse> create(@RequestBody CreateStudentRequest request) { ... }
```

---

## 🔧 Détails d'Implémentation

### 1. Value Object : Money

**Fichier** : `src/main/java/com/school/management/domain/valueobject/Money.java`

**Caractéristiques** :
- Immutable (final fields)
- Validation (montant >= 0)
- Opérations (add, subtract, multiply, divide)
- Comparaison (equals, compareTo)
- JPA Embeddable

**Exemple d'utilisation** :
```java
// Au lieu de
private double amount;

// Utiliser
@Embedded
private Money amount;

// Opérations
Money total = payment1.getAmount().add(payment2.getAmount());
Money perSession = total.divide(sessionCount);
```

---

### 2. PaymentService Divisé

#### PaymentService (CRUD uniquement)

**Responsabilités** :
- Créer un paiement de base
- Récupérer un paiement
- Récupérer les paiements d'un étudiant (paginé)
- Supprimer un paiement

**Méthodes** :
```java
PaymentDTO createPayment(CreatePaymentRequest request)
PaymentDTO findById(Long id)
Page<PaymentDTO> findByStudentId(Long studentId, Pageable pageable)
void deletePayment(Long id)
```

#### PaymentProcessingService

**Responsabilités** :
- Traiter un paiement pour une série complète
- Traiter un paiement de rattrapage
- Valider les requêtes de paiement

**Méthodes** :
```java
PaymentDTO processSeriesPayment(ProcessPaymentRequest request)
PaymentDTO processCatchUpPayment(CatchUpPaymentRequest request)
void validatePaymentRequest(ProcessPaymentRequest request)
```

#### PaymentDistributionService

**Responsabilités** :
- Distribuer un paiement sur les sessions
- Recalculer la distribution
- Vérifier la présence d'un étudiant

**Méthodes** :
```java
void distributePaymentToSessions(Payment payment)
void recalculateDistribution(Long paymentId)
boolean wasStudentPresent(Student student, Session session)
```

#### PaymentStatusService

**Responsabilités** :
- Calculer le statut de paiement pour un groupe
- Calculer le statut pour un étudiant
- Calculer les détails par série/session

**Méthodes** :
```java
List<StudentPaymentStatus> getGroupPaymentStatus(Long groupId)
StudentPaymentStatus getStudentPaymentStatus(Long studentId, Long groupId)
SeriesPaymentStatus calculateSeriesStatus(Student student, SessionSeries series)
```

---

### 3. PageResponse Générique

**Fichier** : `src/main/java/com/school/management/api/response/common/PageResponse.java`

**Structure** :
```json
{
  "content": [...],
  "metadata": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "first": true,
    "last": false,
    "empty": false
  }
}
```

**Factory Method** :
```java
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
```

---

## ✅ Checklist de Progression

### Value Objects
- [ ] Money.java créé et testé
- [ ] Email.java créé et testé
- [ ] PhoneNumber.java créé et testé
- [ ] DateRange.java créé et testé
- [ ] Entités mises à jour pour utiliser les Value Objects

### Division PaymentService
- [ ] PaymentService (CRUD) créé
- [ ] PaymentDistributionService créé
- [ ] PaymentProcessingService créé
- [ ] PaymentStatusService créé
- [ ] PaymentController mis à jour
- [ ] Tests unitaires pour les 4 services

### Pagination
- [ ] PaginationConfig créé
- [ ] PageResponse créé
- [ ] StudentService paginé
- [ ] GroupService paginé
- [ ] PaymentService paginé
- [ ] TeacherService paginé
- [ ] SessionService paginé
- [ ] Tous les controllers mis à jour

### Request/Response DTOs
- [ ] Request DTOs créés (Student, Payment, Group)
- [ ] Response DTOs créés (Student, Payment, Group)
- [ ] Mappers mis à jour
- [ ] Controllers mis à jour

---

## 📊 Métriques de Succès

### Avant Phase 2
- PaymentService : **496 LOC**
- Endpoints paginés : **0/5**
- DTOs séparés : **0%**
- Value Objects : **0**

### Après Phase 2 (Cible)
- Services Payment : **4 services < 200 LOC chacun**
- Endpoints paginés : **5/5 (100%)**
- DTOs séparés : **100%**
- Value Objects : **4 (Money, Email, PhoneNumber, DateRange)**

---

## 🚀 Ordre d'Exécution

### Sprint 1 (Jours 1-3) : Value Objects & Money
1. Créer Money.java
2. Créer Email.java
3. Créer PhoneNumber.java
4. Créer DateRange.java
5. Mettre à jour PaymentEntity pour utiliser Money
6. Compiler et tester

### Sprint 2 (Jours 4-6) : Division PaymentService
1. Créer PaymentService (CRUD)
2. Créer PaymentDistributionService
3. Créer PaymentProcessingService
4. Créer PaymentStatusService
5. Mettre à jour PaymentController
6. Compiler et tester

### Sprint 3 (Jours 7-9) : Pagination
1. Créer PaginationConfig
2. Créer PageResponse
3. Mettre à jour tous les services
4. Mettre à jour tous les controllers
5. Tester tous les endpoints

### Sprint 4 (Jours 10-12) : Request/Response DTOs
1. Créer Request DTOs
2. Créer Response DTOs
3. Mettre à jour les mappers
4. Mettre à jour les controllers
5. Tests finaux

---

## 🔍 Points d'Attention

### Risques Identifiés
1. **Breaking Changes** - Les changements d'API peuvent casser les clients existants
2. **Migration des Données** - Money nécessite une migration de double → embedded
3. **Tests** - Les services divisés nécessitent plus de tests
4. **Complexité** - Plus de classes = plus de maintenance

### Mitigation
1. **Versioning API** - Ajouter /v2/ pour les nouveaux endpoints
2. **Migration Progressive** - Garder les anciens endpoints en @Deprecated temporairement
3. **Tests Automatisés** - Créer des tests pour chaque nouveau service
4. **Documentation** - Documenter chaque changement dans ce fichier

---

## 📝 Notes de Migration

### Money Value Object

**Migration PaymentEntity** :
```java
// AVANT
@Column(name = "amount")
private double amount;

// APRÈS
@Embedded
@AttributeOverrides({
    @AttributeOverride(name = "amount", column = @Column(name = "amount"))
})
private Money amount;
```

**Pas besoin de migration DB** - La colonne `amount` reste identique, seule la représentation Java change.

---

## 📚 Références

- [REFACTORING_PLAN.md](./REFACTORING_PLAN.md) - Plan global de refactoring
- [PHASE1_FIXES_SUMMARY.md](./PHASE1_FIXES_SUMMARY.md) - Résumé Phase 1
- [ARCHITECTURE_ANALYSIS_SUMMARY.md](./ARCHITECTURE_ANALYSIS_SUMMARY.md) - Analyse initiale

---

**Document créé le** : 2025-12-04
**Auteur** : Claude Code
**Version** : 1.0
