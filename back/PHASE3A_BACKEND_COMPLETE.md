# ✅ Phase 3A - Backend COMPLET (Gestion Photos)

**Date**: 2025-12-07
**Status**: ✅ Code terminé - ⚠️ Compilation bloquée (Java 25 incompatible)

---

## 📊 Résumé

Implémentation complète de la gestion des photos pour Groups et Teachers au backend.

### Modifications Réalisées

#### 1. GroupEntity - Ajout champ photo ✅
**Fichier**: `src/main/java/com/school/management/persistance/GroupEntity.java`

```java
@Column(name = "photo")
private String photo;
```

#### 2. GroupService - Méthodes photo ✅
**Fichiers modifiés**:
- `src/main/java/com/school/management/service/interfaces/GroupService.java`
- `src/main/java/com/school/management/service/group/GroupServiceImpl.java`

**Méthodes ajoutées**:
```java
String uploadPhoto(Long groupId, MultipartFile file) throws IOException;
Resource getPhoto(Long groupId) throws IOException;
```

**Features**:
- Upload avec suppression de l'ancienne photo
- Utilisation de `FileManagementService.uploadWithRollback()` pour sécurité
- Validation et gestion d'erreurs complète

#### 3. GroupController - Endpoints photo ✅
**Fichier**: `src/main/java/com/school/management/controller/GroupController.java`

**Endpoints ajoutés**:
- `POST /api/groups/{id}/photo` - Upload photo
- `GET /api/groups/{id}/photo` - Récupérer photo

#### 4. TeacherService - Méthodes photo ✅
**Fichier**: `src/main/java/com/school/management/service/TeacherService.java`

**Méthodes ajoutées**:
```java
String uploadPhoto(Long teacherId, MultipartFile file) throws IOException;
Resource getPhoto(Long teacherId) throws IOException;
```

**Note**: TeacherEntity hérite de PersonEntity qui a déjà le champ `photo`

#### 5. TeacherController - Endpoints photo ✅
**Fichier**: `src/main/java/com/school/management/controller/TeacherController.java`

**Endpoints ajoutés**:
- `POST /api/teachers/{id}/photo` - Upload photo
- `GET /api/teachers/{id}/photo` - Récupérer photo

**Note**: PUT et DELETE existaient déjà:
- `PUT /api/teachers/{id}` - Update teacher
- `DELETE /api/teachers/disable/{id}` - Soft delete

---

## ⚠️ Problème de Compilation

### Erreur
```
Fatal error compiling: java.lang.ExceptionInInitializerError:
com.sun.tools.javac.code.TypeTag :: UNKNOWN
```

### Cause
Le système utilise **Java 25.0.1** (early-access) qui est incompatible avec:
- Maven Compiler Plugin 3.12.0
- Configuration du projet (Java 21)

### Solution Requise

#### Option 1: Installer Java 21 (Recommandé)
```bash
# macOS - Homebrew
brew install openjdk@21

# Ou SDKMAN
sdk install java 21.0.1-open

# Configurer JAVA_HOME
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

#### Option 2: Downgrade de Java 25 → Java 21
```bash
# Désinstaller Java 25
rm -rf /Users/tayebdj/Library/Java/JavaVirtualMachines/openjdk-25.0.1

# Installer Java 21 (voir Option 1)
```

#### Vérification
```bash
java -version
# Devrait afficher: openjdk version "21.x.x"

./mvnw clean compile -DskipTests
# Devrait compiler sans erreur
```

---

## ✅ Code Backend Complet

### Fichiers Créés/Modifiés

| Fichier | Modifications | LOC |
|---------|--------------|-----|
| `GroupEntity.java` | Ajout champ photo | +2 |
| `GroupService.java` | Signature méthodes photo | +6 |
| `GroupServiceImpl.java` | Implémentation photo | +60 |
| `GroupController.java` | Endpoints photo | +40 |
| `TeacherService.java` | Implémentation photo | +60 |
| `TeacherController.java` | Endpoints photo | +40 |
| **Total** | **6 fichiers** | **+208 LOC** |

---

## 🔧 Endpoints Disponibles (Après compilation)

### Groups
```bash
# Upload photo groupe
curl -X POST http://localhost:8080/api/groups/1/photo \
  -F "file=@group-photo.jpg"

# Récupérer photo groupe
curl http://localhost:8080/api/groups/1/photo \
  -o group-photo-downloaded.jpg
```

### Teachers
```bash
# Upload photo enseignant
curl -X POST http://localhost:8080/api/teachers/1/photo \
  -F "file=@teacher-photo.jpg"

# Récupérer photo enseignant
curl http://localhost:8080/api/teachers/1/photo \
  -o teacher-photo-downloaded.jpg
```

---

## 📝 Notes Techniques

### Validation Fichiers
Le `FileManagementService` valide automatiquement:
- ✅ Type de fichier (JPEG, PNG, etc.)
- ✅ Taille maximale (10 MB par défaut)
- ✅ Nom de fichier sécurisé (pas de path traversal)

### Rollback Automatique
Si l'upload échoue, le fichier est automatiquement supprimé:
```java
FileManagementService.FileUploadResult result =
    fileManagementService.uploadWithRollback(file);

if (!result.isSuccess()) {
    // Fichier automatiquement nettoyé
    throw new IOException(result.getErrorMessage());
}
```

### Suppression Ancienne Photo
Avant d'uploader une nouvelle photo, l'ancienne est supprimée:
```java
if (entity.getPhoto() != null && !entity.getPhoto().isEmpty()) {
    try {
        fileManagementService.deleteFile(entity.getPhoto());
    } catch (IOException e) {
        LOGGER.warn("Failed to delete old photo", e);
        // Continue - on veut uploader la nouvelle photo
    }
}
```

---

## 🎯 Prochaines Étapes

### 1. Résoudre Problème Java ⚠️
```bash
# Installer Java 21
brew install openjdk@21

# Configurer
export JAVA_HOME=$(/usr/libexec/java_home -v 21)

# Tester
./mvnw clean compile -DskipTests
```

### 2. Tester Backend ✅
```bash
# Démarrer l'application
./mvnw spring-boot:run

# Tester upload
curl -X POST http://localhost:8080/api/groups/1/photo \
  -F "file=@test.jpg"
```

### 3. Frontend - GroupService 📋
Ajouter méthodes au frontend (Angular):
```typescript
uploadGroupPhoto(id: number, file: File): Observable<string>
getGroupPhotoUrl(id: number): string
```

### 4. Frontend - TeacherService 📋
Ajouter méthodes au frontend (Angular):
```typescript
uploadTeacherPhoto(id: number, file: File): Observable<string>
getTeacherPhotoUrl(id: number): string
```

---

## ✅ Checklist Backend

- [x] GroupEntity - Champ photo
- [x] GroupService - uploadPhoto()
- [x] GroupService - getPhoto()
- [x] GroupController - POST /photo
- [x] GroupController - GET /photo
- [x] TeacherService - uploadPhoto()
- [x] TeacherService - getPhoto()
- [x] TeacherController - POST /photo
- [x] TeacherController - GET /photo
- [ ] Compilation (bloquée - Java 25)
- [ ] Tests avec Postman/curl

---

**Backend Phase 3A**: ✅ **Code Complet**
**Compilation**: ⚠️ **Bloquée (Java 25 → installer Java 21)**
**Tests**: 📋 **En attente de compilation**

