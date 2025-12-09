# 🧪 Phase 2 - Guide de Test

**Date** : 2025-12-04
**Objectif** : Vérifier que toutes les modifications de Phase 2 compilent et fonctionnent correctement

---

## 📋 Checklist de Test

### ✅ Étape 1 : Compilation dans IntelliJ IDEA

#### 1.1 Ouvrir le Projet
```
1. Ouvrir IntelliJ IDEA
2. File → Open
3. Sélectionner: /Users/tayebdj/IdeaProjects/schoolManagement
4. Attendre que l'indexation soit terminée
```

#### 1.2 Rebuild Project
```
1. Dans IntelliJ: Build → Rebuild Project
2. Attendre la fin de la compilation
3. Vérifier la fenêtre "Build" en bas
```

**Résultat attendu** :
```
BUILD SUCCESSFUL
0 errors, 0 warnings
```

**Si erreurs** :
- Vérifier que JDK 21 est configuré (pas JDK 25)
- File → Invalidate Caches / Restart
- Rebuild Project

---

### ✅ Étape 2 : Vérifier les Nouveaux Fichiers

#### 2.1 Value Objects (4 fichiers)
```
src/main/java/com/school/management/domain/valueobject/
├── Money.java ✅
├── Email.java ✅
├── PhoneNumber.java ✅
└── DateRange.java ✅
```

**Test** :
- Clic droit sur chaque fichier → "Recompile 'FileName.java'"
- Vérifier qu'il n'y a aucune erreur

#### 2.2 Payment Services (4 fichiers)
```
src/main/java/com/school/management/service/payment/
├── PaymentCrudService.java ✅
├── PaymentDistributionService.java ✅
├── PaymentStatusService.java ✅
└── PaymentProcessingService.java ✅
```

**Test** :
- Clic droit sur le package `payment` → "Recompile 'payment'"
- Vérifier qu'il n'y a aucune erreur

#### 2.3 Infrastructure Pagination (2 fichiers)
```
src/main/java/com/school/management/infrastructure/config/web/
└── PaginationConfig.java ✅

src/main/java/com/school/management/api/response/common/
└── PageResponse.java ✅
```

**Test** :
- Vérifier que les packages sont créés
- Recompiler les fichiers

---

### ✅ Étape 3 : Vérifier les Dépendances Spring

#### 3.1 Vérifier l'Auto-configuration
Dans IntelliJ, chercher les beans Spring :

1. **View → Tool Windows → Spring**
2. Vérifier que les beans suivants sont détectés :
   - `paymentCrudService`
   - `paymentProcessingService`
   - `paymentStatusService`
   - `paymentDistributionService`
   - `paginationConfig`

#### 3.2 Vérifier les Injections
Ouvrir `PaymentController.java` :
- Les 3 services doivent être surlignés en vert (injectés par Spring)
- Pas d'erreur "Could not autowire"

---

### ✅ Étape 4 : Lancer l'Application

#### 4.1 Démarrer le Serveur
```
1. Trouver: SchoolManagementApplication.java
2. Clic droit → Run 'SchoolManagementApplication'
3. Attendre le démarrage (console en bas)
```

**Résultat attendu** :
```
Started SchoolManagementApplication in X.XXX seconds
Tomcat started on port(s): 8080 (http)
```

**Si erreur au démarrage** :
- Vérifier les logs dans la console
- Chercher "ERROR" ou "WARN" en rouge
- Vérifier que le port 8080 n'est pas déjà utilisé

#### 4.2 Vérifier les Endpoints
Une fois démarré, vérifier dans les logs :
```
Mapped "{[/api/payments]}" onto public org.springframework.http.ResponseEntity...
Mapped "{[/api/payments/student/{studentId}]}" onto public org.springframework.http.ResponseEntity...
```

---

### ✅ Étape 5 : Tester les Endpoints

#### 5.1 Ouvrir un Terminal/Postman

**Option A : Terminal (curl)**
```bash
# Test 1: Tous les paiements (paginé)
curl http://localhost:8080/api/payments?page=0&size=20

# Test 2: Paiements d'un étudiant (paginé)
curl http://localhost:8080/api/payments/student/1?page=0&size=10

# Test 3: Statut de paiement d'un groupe
curl http://localhost:8080/api/payments/1/students-payment-status

# Test 4: Sessions impayées d'un étudiant
curl http://localhost:8080/api/payments/students/1/unpaid-sessions
```

**Option B : Navigateur**
```
http://localhost:8080/api/payments?page=0&size=20
http://localhost:8080/api/payments/student/1?page=0&size=10
```

#### 5.2 Vérifier la Réponse JSON

**Format attendu pour endpoints paginés** :
```json
{
  "content": [
    {
      "id": 1,
      "studentId": 1,
      "amountPaid": 500.0,
      ...
    }
  ],
  "metadata": {
    "page": 0,
    "size": 20,
    "totalElements": 150,
    "totalPages": 8,
    "first": true,
    "last": false,
    "empty": false,
    "hasNext": true,
    "hasPrevious": false
  }
}
```

#### 5.3 Tester le Traitement de Paiement

**Option A : curl**
```bash
curl -X POST http://localhost:8080/api/payments/process \
  -H "Content-Type: application/json" \
  -d '{
    "studentId": 1,
    "groupId": 1,
    "sessionSeriesId": 1,
    "amountPaid": 500.00
  }'
```

**Option B : Postman**
```
POST http://localhost:8080/api/payments/process
Content-Type: application/json

{
  "studentId": 1,
  "groupId": 1,
  "sessionSeriesId": 1,
  "amountPaid": 500.00
}
```

**Réponse attendue** :
```json
{
  "id": 123,
  "studentId": 1,
  "groupId": 1,
  "sessionSeriesId": 1,
  "amountPaid": 500.0,
  "status": "In Progress",
  ...
}
```

---

### ✅ Étape 6 : Vérifier les Logs

#### 6.1 Logs de l'Application
Dans la console IntelliJ, chercher :

**Logs de démarrage** :
```
INFO  PaymentCrudService - ...
INFO  PaymentProcessingService - ...
INFO  PaymentStatusService - ...
INFO  PaymentDistributionService - ...
```

**Logs lors des requêtes** :
```
INFO  PaymentController - Fetching all payments - page: 0, size: 20
INFO  PaymentCrudService - Fetching all payments - page: 0, size: 20
INFO  PaymentController - Processing payment - student: 1, group: 1, series: 1, amount: 500.0
```

#### 6.2 Vérifier Aucune Erreur
Chercher dans les logs :
- ❌ "ERROR" en rouge
- ❌ "Exception"
- ❌ "NullPointerException"
- ❌ "Could not autowire"

Si présent, noter l'erreur et la ligne de code.

---

## 🐛 Problèmes Courants et Solutions

### Problème 1 : Erreur de Compilation
```
Error: cannot find symbol PaymentCrudService
```

**Solution** :
1. File → Invalidate Caches / Restart
2. Build → Rebuild Project
3. Vérifier que les packages sont corrects

---

### Problème 2 : Could not autowire
```
Could not autowire. No beans of 'PaymentCrudService' type found.
```

**Solution** :
1. Vérifier que `@Service` est présent sur la classe
2. Vérifier que le package est scanné par Spring
3. Rebuild Project

---

### Problème 3 : Application ne démarre pas
```
Error starting ApplicationContext
```

**Solution** :
1. Vérifier les logs pour l'erreur exacte
2. Vérifier que toutes les dépendances sont injectées
3. Vérifier qu'il n'y a pas de dépendances circulaires

---

### Problème 4 : Endpoint retourne 404
```
GET /api/payments → 404 Not Found
```

**Solution** :
1. Vérifier que l'application a bien démarré
2. Vérifier les logs : "Mapped "{[/api/payments]}"
3. Vérifier que le port est bien 8080

---

### Problème 5 : Endpoint retourne 500
```
GET /api/payments → 500 Internal Server Error
```

**Solution** :
1. Regarder les logs dans la console
2. Identifier la ligne qui cause l'exception
3. Vérifier que la base de données est accessible

---

## ✅ Checklist Finale

### Compilation
- [ ] Projet compile sans erreurs dans IntelliJ
- [ ] Tous les nouveaux fichiers compilent
- [ ] Aucun warning critique

### Démarrage
- [ ] Application démarre sans erreur
- [ ] Tous les beans Spring sont créés
- [ ] Endpoints mappés correctement

### Endpoints Testés
- [ ] `GET /api/payments?page=0&size=20` → 200 OK
- [ ] `GET /api/payments/student/1?page=0&size=10` → 200 OK
- [ ] `POST /api/payments/process` → 200 OK
- [ ] `GET /api/payments/1/students-payment-status` → 200 OK
- [ ] Réponses au format `PageResponse` pour endpoints paginés

### Logs
- [ ] Aucune erreur dans les logs
- [ ] Logs de tous les services présents
- [ ] Pagination fonctionne (logs montrent page, size)

---

## 📊 Résultat Attendu

### ✅ Succès Complet
```
✓ Compilation: 0 erreurs
✓ Démarrage: OK
✓ Endpoints: Tous répondent 200 OK
✓ Pagination: Fonctionne (format PageResponse)
✓ Services: Tous injectés et fonctionnels
✓ Logs: Aucune erreur
```

**Action** : Phase 2 validée ✅ Passer à Phase 3 ou arrêter ici

---

### ⚠️ Succès Partiel
```
✓ Compilation: 0 erreurs
✓ Démarrage: OK
⚠ Endpoints: Certains retournent 404 ou 500
⚠ Logs: Quelques warnings
```

**Action** : Corriger les endpoints qui ne fonctionnent pas

---

### ❌ Échec
```
✗ Compilation: Erreurs présentes
ou
✗ Démarrage: Application ne démarre pas
```

**Action** :
1. Noter toutes les erreurs
2. Vérifier les dépendances
3. Invalidate Caches / Restart
4. Demander de l'aide avec les logs d'erreur

---

## 📝 Rapport de Test

Une fois les tests terminés, noter les résultats :

```
Date: 2025-12-04
Testeur: [Votre nom]

Compilation: ✅ / ❌
Démarrage: ✅ / ❌
Endpoints testés: X/Y fonctionnent

Problèmes rencontrés:
1. [Description du problème 1]
2. [Description du problème 2]

Notes:
- [Autres observations]
```

---

## 🎯 Prochaines Étapes

### Si Tous les Tests Passent ✅
**Options** :
1. **Arrêter ici** - Phase 2 est complète et fonctionnelle
2. **Continuer Phase 3** - Paginer les autres controllers (Student, Group, etc.)
3. **Ajouter des tests unitaires** - Pour les nouveaux services
4. **Créer un commit Git** - Sauvegarder Phase 2

### Si Certains Tests Échouent ⚠️
**Actions** :
1. Noter les erreurs exactes
2. Vérifier les logs
3. Corriger les problèmes un par un
4. Re-tester

### Si Compilation Échoue ❌
**Actions** :
1. Noter toutes les erreurs de compilation
2. Vérifier les imports
3. Invalidate Caches / Restart
4. Rebuild Project
5. Demander de l'aide avec les messages d'erreur

---

**Document créé** : 2025-12-04
**Auteur** : Claude Code
**Objectif** : Valider Phase 2 avant de continuer
