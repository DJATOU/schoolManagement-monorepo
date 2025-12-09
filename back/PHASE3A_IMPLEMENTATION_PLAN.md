# 🚀 Phase 3A - CRUD Complet + Photos

**Date début**: 2025-12-04
**Objectif**: Compléter les opérations CRUD sur Groupes et Teachers + Gestion des photos
**Durée estimée**: 1-2 semaines
**Priorité**: 🔴 CRITIQUE

---

## 🎯 Objectifs

### Backend
1. ✅ GroupController - PUT (update group)
2. ✅ GroupController - DELETE (soft delete)
3. ✅ GroupController - POST/GET photo
4. ✅ TeacherController - PUT (update teacher)
5. ✅ TeacherController - DELETE (soft delete)
6. ✅ TeacherController - POST/GET photo

### Frontend
1. ✅ GroupService - updateGroup(), deleteGroup(), uploadPhoto()
2. ✅ TeacherService - updateTeacher(), deleteTeacher(), uploadPhoto()
3. ✅ Composant EditGroupDialog
4. ✅ Composant EditTeacherDialog
5. ✅ Upload/Display photos dans cartes

---

## 📋 Plan d'Implémentation

### Étape 1: Backend - GroupController ✅

#### 1.1 Update Group
```java
@PutMapping("/{id}")
public ResponseEntity<GroupDTO> updateGroup(
    @PathVariable Long id,
    @RequestBody @Valid GroupDTO groupDto
) {
    GroupEntity updatedGroup = groupService.updateGroup(id, groupDto);
    return ResponseEntity.ok(groupMapper.toDto(updatedGroup));
}
```

#### 1.2 Delete Group (Soft Delete)
```java
@DeleteMapping("/{id}")
public ResponseEntity<Void> deleteGroup(@PathVariable Long id) {
    groupService.deleteGroup(id);
    return ResponseEntity.noContent().build();
}
```

#### 1.3 Upload Photo
```java
@PostMapping("/{id}/photo")
public ResponseEntity<String> uploadGroupPhoto(
    @PathVariable Long id,
    @RequestParam("file") MultipartFile file
) {
    String photoUrl = groupService.uploadPhoto(id, file);
    return ResponseEntity.ok(photoUrl);
}

@GetMapping("/{id}/photo")
public ResponseEntity<Resource> getGroupPhoto(@PathVariable Long id) {
    Resource photo = groupService.getPhoto(id);
    return ResponseEntity.ok()
        .contentType(MediaType.IMAGE_JPEG)
        .body(photo);
}
```

---

### Étape 2: Backend - GroupService ✅

```java
@Service
public class GroupServiceImpl implements GroupService {

    @Transactional
    public GroupEntity updateGroup(Long id, GroupDTO groupDto) {
        GroupEntity group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Group", id));

        // Update fields
        group.setName(groupDto.getName());
        group.setType(groupDto.getType());
        // ... autres champs

        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long id) {
        GroupEntity group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Group", id));

        group.setActive(false);  // Soft delete
        groupRepository.save(group);
    }

    @Transactional
    public String uploadPhoto(Long id, MultipartFile file) {
        GroupEntity group = groupRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Group", id));

        String photoUrl = fileStorageService.storeFile(file, "groups");
        group.setPhotoUrl(photoUrl);
        groupRepository.save(group);

        return photoUrl;
    }
}
```

---

### Étape 3: Backend - TeacherController ✅

Même structure que GroupController:
- PUT /{id}
- DELETE /{id}
- POST /{id}/photo
- GET /{id}/photo

---

### Étape 4: Backend - TeacherService ✅

Même structure que GroupService:
- updateTeacher()
- deleteTeacher()
- uploadPhoto()

---

### Étape 5: Frontend - GroupService ✅

```typescript
// group.service.ts

/**
 * Met à jour un groupe
 */
updateGroup(id: number, group: Partial<Group>): Observable<Group> {
  return this.http.put<Group>(`${this.apiUrl}/${id}`, group).pipe(
    catchError(this.handleError)
  );
}

/**
 * Supprime un groupe (soft delete)
 */
deleteGroup(id: number): Observable<void> {
  return this.http.delete<void>(`${this.apiUrl}/${id}`).pipe(
    catchError(this.handleError)
  );
}

/**
 * Upload photo du groupe
 */
uploadGroupPhoto(id: number, file: File): Observable<string> {
  const formData = new FormData();
  formData.append('file', file);

  return this.http.post<string>(`${this.apiUrl}/${id}/photo`, formData).pipe(
    catchError(this.handleError)
  );
}

/**
 * Récupère l'URL de la photo du groupe
 */
getGroupPhotoUrl(id: number): string {
  return `${this.apiUrl}/${id}/photo`;
}
```

---

### Étape 6: Frontend - TeacherService ✅

Même structure que GroupService:
- updateTeacher()
- deleteTeacher()
- uploadTeacherPhoto()
- getTeacherPhotoUrl()

---

### Étape 7: Frontend - EditGroupDialog ✅

```typescript
// edit-group-dialog.component.ts

@Component({
  selector: 'app-edit-group-dialog',
  templateUrl: './edit-group-dialog.component.html'
})
export class EditGroupDialogComponent implements OnInit {

  groupForm: FormGroup;
  selectedFile: File | null = null;

  constructor(
    private dialogRef: MatDialogRef<EditGroupDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { group: Group },
    private groupService: GroupService,
    private fb: FormBuilder
  ) {}

  ngOnInit() {
    this.groupForm = this.fb.group({
      name: [this.data.group.name, Validators.required],
      type: [this.data.group.type, Validators.required],
      // ... autres champs
    });
  }

  onFileSelected(event: any) {
    this.selectedFile = event.target.files[0];
  }

  onSave() {
    if (this.groupForm.valid) {
      const updatedGroup = { ...this.data.group, ...this.groupForm.value };

      this.groupService.updateGroup(this.data.group.id, updatedGroup)
        .subscribe(() => {
          if (this.selectedFile) {
            this.groupService.uploadGroupPhoto(this.data.group.id, this.selectedFile)
              .subscribe(() => {
                this.dialogRef.close(true);
              });
          } else {
            this.dialogRef.close(true);
          }
        });
    }
  }
}
```

---

### Étape 8: Frontend - Affichage Photos ✅

#### Dans group-card.component.html
```html
<mat-card class="group-card">
  <!-- Photo du groupe -->
  <img
    mat-card-image
    [src]="getGroupPhotoUrl(group.id)"
    [alt]="group.name"
    (error)="onImageError($event)"
  />

  <mat-card-header>
    <mat-card-title>{{ group.name }}</mat-card-title>
  </mat-card-header>

  <mat-card-actions>
    <button mat-icon-button (click)="onEdit(group)">
      <mat-icon>edit</mat-icon>
    </button>
    <button mat-icon-button color="warn" (click)="onDelete(group)">
      <mat-icon>delete</mat-icon>
    </button>
  </mat-card-actions>
</mat-card>
```

#### Dans group-card.component.ts
```typescript
getGroupPhotoUrl(groupId: number): string {
  return this.groupService.getGroupPhotoUrl(groupId);
}

onImageError(event: any) {
  // Afficher une image par défaut si erreur
  event.target.src = 'assets/images/default-group.png';
}

onEdit(group: Group) {
  const dialogRef = this.dialog.open(EditGroupDialogComponent, {
    width: '600px',
    data: { group }
  });

  dialogRef.afterClosed().subscribe(result => {
    if (result) {
      this.loadGroups();  // Recharger la liste
    }
  });
}

onDelete(group: Group) {
  const dialogRef = this.dialog.open(ConfirmDialogComponent, {
    data: {
      title: 'Supprimer le groupe?',
      message: `Êtes-vous sûr de vouloir supprimer le groupe "${group.name}"?`
    }
  });

  dialogRef.afterClosed().subscribe(confirmed => {
    if (confirmed) {
      this.groupService.deleteGroup(group.id).subscribe(() => {
        this.loadGroups();  // Recharger la liste
      });
    }
  });
}
```

---

## 🗂️ Structure des Fichiers

### Backend
```
src/main/java/com/school/management/
├── controller/
│   ├── GroupController.java         ✅ Ajouter PUT, DELETE, POST photo
│   └── TeacherController.java       ✅ Ajouter PUT, DELETE, POST photo
├── service/
│   ├── interfaces/
│   │   ├── GroupService.java        ✅ Ajouter méthodes
│   │   └── TeacherService.java      ✅ Ajouter méthodes
│   └── group/
│       └── GroupServiceImpl.java    ✅ Implémenter
└── infrastructure/
    └── storage/
        └── FileManagementService.java ✅ Déjà existe
```

### Frontend
```
src/app/
├── services/
│   ├── group.service.ts             ✅ Ajouter méthodes
│   └── teacher.service.ts           ✅ Ajouter méthodes
├── components/
│   ├── group/
│   │   ├── edit-group-dialog/       ✅ Créer
│   │   ├── group-card/              ✅ Modifier (ajouter photo)
│   │   └── group-list/              ✅ Modifier (boutons edit/delete)
│   └── teacher/
│       ├── edit-teacher-dialog/     ✅ Créer
│       ├── teacher-card/            ✅ Modifier (ajouter photo)
│       └── teacher-list/            ✅ Modifier (boutons edit/delete)
└── assets/
    └── images/
        ├── default-group.png        ✅ Ajouter
        └── default-teacher.png      ✅ Ajouter
```

---

## 🔧 Configuration Requise

### Backend - application.properties
```properties
# File Upload Configuration
spring.servlet.multipart.enabled=true
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB

# File Storage Path
file.storage.location=./uploads
file.storage.groups=${file.storage.location}/groups
file.storage.teachers=${file.storage.location}/teachers
file.storage.students=${file.storage.location}/students
```

### Backend - Créer les dossiers
```bash
mkdir -p uploads/groups
mkdir -p uploads/teachers
mkdir -p uploads/students
```

---

## ✅ Checklist d'Implémentation

### Backend - GroupController
- [ ] PUT /api/groups/{id}
- [ ] DELETE /api/groups/{id}
- [ ] POST /api/groups/{id}/photo
- [ ] GET /api/groups/{id}/photo

### Backend - GroupService
- [ ] updateGroup(id, groupDto)
- [ ] deleteGroup(id)
- [ ] uploadPhoto(id, file)
- [ ] getPhoto(id)

### Backend - TeacherController
- [ ] PUT /api/teachers/{id}
- [ ] DELETE /api/teachers/{id}
- [ ] POST /api/teachers/{id}/photo
- [ ] GET /api/teachers/{id}/photo

### Backend - TeacherService
- [ ] updateTeacher(id, teacherDto)
- [ ] deleteTeacher(id)
- [ ] uploadPhoto(id, file)
- [ ] getPhoto(id)

### Frontend - GroupService
- [ ] updateGroup(id, group)
- [ ] deleteGroup(id)
- [ ] uploadGroupPhoto(id, file)
- [ ] getGroupPhotoUrl(id)

### Frontend - TeacherService
- [ ] updateTeacher(id, teacher)
- [ ] deleteTeacher(id)
- [ ] uploadTeacherPhoto(id, file)
- [ ] getTeacherPhotoUrl(id)

### Frontend - Composants
- [ ] EditGroupDialogComponent
- [ ] EditTeacherDialogComponent
- [ ] GroupCard - Affichage photo
- [ ] GroupCard - Boutons edit/delete
- [ ] TeacherCard - Affichage photo
- [ ] TeacherCard - Boutons edit/delete

### Tests
- [ ] Backend: Test update group
- [ ] Backend: Test delete group
- [ ] Backend: Test upload photo group
- [ ] Backend: Test update teacher
- [ ] Backend: Test delete teacher
- [ ] Backend: Test upload photo teacher
- [ ] Frontend: Test GroupService
- [ ] Frontend: Test TeacherService
- [ ] E2E: Test complet CRUD groupe
- [ ] E2E: Test complet CRUD teacher

---

## 🚀 Ordre d'Exécution

### Jour 1-2: Backend Groups
1. ✅ GroupController - PUT
2. ✅ GroupController - DELETE
3. ✅ GroupController - POST/GET photo
4. ✅ GroupServiceImpl - Implémentations
5. ✅ Tests Postman/curl

### Jour 3-4: Backend Teachers
1. ✅ TeacherController - PUT
2. ✅ TeacherController - DELETE
3. ✅ TeacherController - POST/GET photo
4. ✅ TeacherServiceImpl - Implémentations
5. ✅ Tests Postman/curl

### Jour 5-7: Frontend Groups
1. ✅ GroupService - Nouvelles méthodes
2. ✅ EditGroupDialogComponent
3. ✅ GroupCard - Modifications
4. ✅ Tests

### Jour 8-10: Frontend Teachers
1. ✅ TeacherService - Nouvelles méthodes
2. ✅ EditTeacherDialogComponent
3. ✅ TeacherCard - Modifications
4. ✅ Tests

### Jour 11-12: Tests & Documentation
1. ✅ Tests E2E complets
2. ✅ Documentation API
3. ✅ Guide utilisateur

---

## 📊 Critères de Succès

### Backend
- ✅ Tous les endpoints répondent 200 OK
- ✅ Upload de photos fonctionne (JPEG, PNG, < 10MB)
- ✅ Soft delete fonctionne (active = false)
- ✅ Update fonctionne (tous les champs)
- ✅ Pas d'erreurs 500

### Frontend
- ✅ Formulaires de modification fonctionnent
- ✅ Upload de photos fonctionne
- ✅ Photos s'affichent dans les cartes
- ✅ Confirmation avant suppression
- ✅ Messages d'erreur clairs
- ✅ Rechargement automatique après modification

---

## 🎯 Livrable Final

**Phase 3A Complète**:
- ✅ CRUD 100% complet sur Groupes
- ✅ CRUD 100% complet sur Teachers
- ✅ Gestion des photos (upload + affichage)
- ✅ Interface utilisateur complète
- ✅ Tests passent
- ✅ Documentation à jour

---

**Document créé**: 2025-12-04
**Status**: 🚀 EN COURS
**Next**: Implémentation Backend GroupController
