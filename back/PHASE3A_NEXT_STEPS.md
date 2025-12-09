# 🚀 Phase 3A - Prochaines Étapes

**Date**: 2025-12-07
**Progress**: 60% Terminé

---

## ⚠️ ACTION URGENTE - Java 21

### Problème Actuel
Le backend **ne compile pas** car Java 25 (early-access) est installé au lieu de Java 21.

### Solution (5 minutes)

#### Option 1: Homebrew (Recommandé pour macOS)
```bash
# Installer Java 21
brew install openjdk@21

# Configurer JAVA_HOME
echo 'export JAVA_HOME=$(/usr/libexec/java_home -v 21)' >> ~/.zshrc
source ~/.zshrc

# OU temporairement pour cette session
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
```

#### Option 2: SDKMAN
```bash
# Installer SDKMAN si pas déjà fait
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"

# Installer Java 21
sdk install java 21.0.1-open

# Utiliser Java 21
sdk use java 21.0.1-open
```

### Vérification
```bash
# Vérifier la version
java -version
# Devrait afficher: openjdk version "21.x.x"

# Tester la compilation
cd /Users/tayebdj/IdeaProjects/schoolManagement
./mvnw clean compile -DskipTests
# Devrait compiler sans erreur
```

---

## ✅ Ce Qui Est Fait (60%)

### Backend - Code Complet ✅
- [x] GroupEntity - Champ `photo`
- [x] GroupService - `uploadPhoto()`, `getPhoto()`
- [x] GroupController - `POST /photo`, `GET /photo`
- [x] TeacherService - `uploadPhoto()`, `getPhoto()`
- [x] TeacherController - `POST /photo`, `GET /photo`

**Total**: 6 fichiers, +208 LOC

### Frontend Services - Terminés ✅
- [x] GroupService - `uploadGroupPhoto()`, `getGroupPhotoUrl()`
- [x] TeacherService - `uploadTeacherPhoto()`, `getTeacherPhotoUrl()`

**Total**: 2 fichiers, +52 LOC

---

## 📋 Ce Qui Reste (40%)

### 1. Tester Backend (Après Java 21)

```bash
# Démarrer le backend
cd /Users/tayebdj/IdeaProjects/schoolManagement
./mvnw spring-boot:run

# Dans un autre terminal - Tester upload groupe
curl -X POST http://localhost:8080/api/groups/1/photo \
  -F "file=@/path/to/test-image.jpg"

# Devrait retourner: "filename-uuid.jpg"

# Récupérer la photo
curl http://localhost:8080/api/groups/1/photo -o group-photo.jpg

# Tester upload teacher
curl -X POST http://localhost:8080/api/teachers/1/photo \
  -F "file=@/path/to/teacher-photo.jpg"
```

### 2. Frontend - Composants UI (4 composants)

#### A. EditGroupDialogComponent

**Créer les fichiers**:
```bash
cd /Users/tayebdj/IdeaProjects/schoolManagement-Font
ng generate component components/group/edit-group-dialog
```

**Contenu** (voir `PHASE3A_SUMMARY.md` section "Frontend - Composants UI"):
- Formulaire de modification
- Input file pour photo
- Preview de l'image
- Gestion upload

#### B. EditTeacherDialogComponent

```bash
ng generate component components/teacher/edit-teacher-dialog
```

Structure similaire à EditGroupDialog.

#### C. Modifier GroupCard

**Fichier**: `src/app/components/group/group-card/group-card.component.html`

Ajouter:
```html
<!-- Photo avec fallback -->
<img
  [src]="groupService.getGroupPhotoUrl(group.id)"
  [alt]="group.name"
  (error)="onImageError($event)"
  class="group-photo"
/>

<!-- Boutons edit/delete -->
<button mat-icon-button (click)="onEdit(group)">
  <mat-icon>edit</mat-icon>
</button>
<button mat-icon-button color="warn" (click)="onDelete(group)">
  <mat-icon>delete</mat-icon>
</button>
```

**Fichier**: `group-card.component.ts`

```typescript
onImageError(event: Event) {
  const img = event.target as HTMLImageElement;
  img.src = 'assets/images/default-group.png';
}

onEdit(group: Group) {
  const dialogRef = this.dialog.open(EditGroupDialogComponent, {
    width: '600px',
    data: { group }
  });

  dialogRef.afterClosed().subscribe(result => {
    if (result) {
      this.loadGroups();
    }
  });
}
```

#### D. Modifier TeacherCard

Structure similaire à GroupCard.

### 3. Assets - Images Par Défaut

**Créer les images**:
```bash
cd /Users/tayebdj/IdeaProjects/schoolManagement-Font/src/assets/images

# Créer ou télécharger:
# - default-group.png (400x400px)
# - default-teacher.png (400x400px)
```

**Recommandations**:
- Format: PNG avec transparence
- Taille: 400x400px
- Poids: < 50KB
- Style: Icône simple, minimaliste

---

## 🗓️ Planning Suggéré

### Session 1 (1-2h) - Déblocage Backend
1. ⚠️ Installer Java 21 (5 min)
2. ✅ Compiler backend (1 min)
3. ✅ Démarrer backend (1 min)
4. ✅ Tester uploads avec curl (10 min)
5. ✅ Vérifier que les photos sont sauvegardées

### Session 2 (2-3h) - Frontend Groups
1. Créer EditGroupDialogComponent
2. Implémenter formulaire + upload
3. Modifier GroupCard pour afficher photos
4. Ajouter boutons edit/delete
5. Créer default-group.png

### Session 3 (2-3h) - Frontend Teachers
1. Créer EditTeacherDialogComponent
2. Implémenter formulaire + upload
3. Modifier TeacherCard pour afficher photos
4. Ajouter boutons edit/delete
5. Créer default-teacher.png

### Session 4 (1h) - Tests & Polish
1. Tests E2E complets
2. Corrections bugs
3. Améliorer UX (loading, messages d'erreur)
4. Documentation utilisateur

---

## 📊 Checklist Rapide

### Avant de Coder
- [ ] ⚠️ Java 21 installé
- [ ] ✅ Backend compile
- [ ] ✅ Backend démarre

### Backend Tests
- [ ] Upload photo groupe fonctionne
- [ ] Upload photo teacher fonctionne
- [ ] GET photo retourne l'image
- [ ] Suppression ancienne photo fonctionne

### Frontend Groups
- [ ] EditGroupDialogComponent créé
- [ ] Upload photo dans dialog fonctionne
- [ ] Preview photo fonctionne
- [ ] GroupCard affiche photo
- [ ] Bouton edit ouvre dialog
- [ ] Image par défaut si pas de photo

### Frontend Teachers
- [ ] EditTeacherDialogComponent créé
- [ ] Upload photo dans dialog fonctionne
- [ ] Preview photo fonctionne
- [ ] TeacherCard affiche photo
- [ ] Bouton edit ouvre dialog
- [ ] Image par défaut si pas de photo

### Assets
- [ ] default-group.png ajouté
- [ ] default-teacher.png ajouté

---

## 💡 Commandes Utiles

### Backend
```bash
# Compiler
./mvnw clean compile -DskipTests

# Démarrer
./mvnw spring-boot:run

# Tests unitaires
./mvnw test

# Package
./mvnw clean package -DskipTests
```

### Frontend
```bash
# Installer dépendances
npm install

# Démarrer dev server
ng serve

# Créer composant
ng generate component components/[path]/[name]

# Build production
ng build --configuration production

# Linter
ng lint
```

---

## 📁 Documentation

### Créée
1. **PHASE3A_IMPLEMENTATION_PLAN.md** - Plan initial
2. **PHASE3A_BACKEND_COMPLETE.md** - Backend détaillé
3. **PHASE3A_FRONTEND_SERVICES.md** - Services frontend
4. **PHASE3A_SUMMARY.md** - Résumé global
5. **PHASE3A_NEXT_STEPS.md** - Ce guide

### À Lire En Premier
1. **PHASE3A_SUMMARY.md** - Vue d'ensemble
2. **PHASE3A_NEXT_STEPS.md** - Ce fichier (actions concrètes)
3. **PHASE3A_BACKEND_COMPLETE.md** - Si problème backend
4. **PHASE3A_FRONTEND_SERVICES.md** - Pour créer les composants

---

## 🎯 Objectif Final Phase 3A

### Fonctionnalités Complètes
- ✅ Créer groupe avec photo
- ✅ Modifier groupe + changer photo
- ✅ Afficher photo dans carte groupe
- ✅ Créer teacher avec photo
- ✅ Modifier teacher + changer photo
- ✅ Afficher photo dans carte teacher
- ✅ Image par défaut si pas de photo
- ✅ Soft delete groupe/teacher

### Critères de Succès
- Backend compile et démarre ✅
- Upload photos fonctionne (JPEG, PNG) ✅
- Photos s'affichent dans les cartes ✅
- Modification avec upload photo ✅
- Gestion d'erreurs claire ✅
- UX fluide et intuitive ✅

---

## 🚀 Commencer Maintenant

### Étape 1: Java 21 (URGENT)
```bash
brew install openjdk@21
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
java -version
```

### Étape 2: Compiler Backend
```bash
cd /Users/tayebdj/IdeaProjects/schoolManagement
./mvnw clean compile -DskipTests
```

### Étape 3: Tester
```bash
# Terminal 1: Démarrer backend
./mvnw spring-boot:run

# Terminal 2: Tester upload
curl -X POST http://localhost:8080/api/groups/1/photo \
  -F "file=@test.jpg"
```

**Après ces 3 étapes**: Backend Phase 3A est 100% fonctionnel! 🎉

---

**Status Actuel**: 60% Terminé
**Blocage**: Java 21 requis (facile à résoudre)
**Prochaine session**: Frontend UI (composants)

**Bonne continuation!** 🚀

