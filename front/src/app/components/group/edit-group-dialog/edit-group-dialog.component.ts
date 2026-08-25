import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogRef, MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { Group } from '../../../models/group/group';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTooltipModule } from '@angular/material/tooltip';
import { TranslateModule } from '@ngx-translate/core';
import { GroupService } from '../../../services/group.service';
import { GroupTypeService } from '../../../services/GroupTypeService';
import { LevelService } from '../../../services/level.service';
import { SubjectService } from '../../../services/subject.service';
import { PricingService } from '../../../services/pricing.service';
import { TeacherService } from '../../../services/teacher.service';
import { GroupType } from '../../../models/GroupType/groupType';
import { Level } from '../../../models/level/level';
import { Subject } from '../../../models/subject/subject';
import { Pricing } from '../../../models/pricing/pricing';
import { Teacher } from '../../../models/teacher/teacher';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { SecureImageDirective } from '../../../shared/secure-image.directive';

@Component({
  selector: 'app-edit-group-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatFormFieldModule,
    MatInputModule,
    MatButtonModule,
    MatSelectModule,
    MatIconModule,
    MatTabsModule,
    MatTooltipModule,
    TranslateModule,
    AdminOnlyDirective
  ,
    SecureImageDirective
  ],
  templateUrl: './edit-group-dialog.component.html',
  styleUrls: ['./edit-group-dialog.component.scss']
})
export class EditGroupDialogComponent implements OnInit {
  editGroupForm!: FormGroup;
  selectedFile: File | null = null;
  photoPreview: string | null = null;

  /** Vrai si la photo référencée est introuvable : on affiche alors un visuel de repli. */
  photoError = false;

  /** Indique que la photo doit être retirée (transmis à l'appelant). */
  shouldClearPhoto = false;

  groupTypes: GroupType[] = [];
  levels: Level[] = [];
  subjects: Subject[] = [];
  prices: Pricing[] = [];
  teachers: Teacher[] = [];

  constructor(
    public dialogRef: MatDialogRef<EditGroupDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { group: Group },
    private fb: FormBuilder,
    private groupService: GroupService,
    private groupTypeService: GroupTypeService,
    private levelService: LevelService,
    private subjectService: SubjectService,
    private pricingService: PricingService,
    private teacherService: TeacherService
  ) {}

  ngOnInit(): void {
    this.editGroupForm = this.fb.group({
      name: [this.data.group.name, Validators.required],
      groupTypeId: [this.data.group.groupTypeId],
      levelId: [this.data.group.levelId],
      subjectId: [this.data.group.subjectId],
      sessionNumberPerSerie: [this.data.group.sessionNumberPerSerie, [Validators.min(1)]],
      priceId: [this.data.group.priceId],
      description: [this.data.group.description],
      teacherId: [this.data.group.teacherId]
    });

    this.loadSelectOptions();

    if (this.data.group.photo) {
      this.photoPreview = this.groupService.getGroupPhotoUrl(this.data.group.id!);
    }
  }

  loadSelectOptions(): void {
    this.groupTypeService.getAllGroupTypes().subscribe(data => this.groupTypes = data);
    this.levelService.getLevels().subscribe(data => this.levels = data);
    this.subjectService.getSubjects().subscribe(data => this.subjects = data);
    this.pricingService.getPricings().subscribe(data => this.prices = data);
    this.teacherService.getTeachers().subscribe(data => this.teachers = data);
  }

  onFileSelected(event: Event): void {
    const target = event.target as HTMLInputElement;
    if (target?.files?.length) {
      this.selectedFile = target.files[0];

      const reader = new FileReader();
      reader.onload = (e) => {
        this.photoPreview = e.target?.result as string;
      };
      reader.readAsDataURL(this.selectedFile);
      this.photoError = false;
      this.shouldClearPhoto = false;
    }
  }

  clearPhoto(): void {
    this.selectedFile = null;
    this.photoPreview = null;
    this.photoError = false;
    this.shouldClearPhoto = true;
  }

  /**
   * La photo référencée est introuvable (fichier absent côté serveur) : on bascule sur un
   * visuel de repli plutôt que d'afficher une image cassée.
   */
  onPhotoError(): void {
    this.photoError = true;
  }

  onCancel(): void {
    this.dialogRef.close();
  }

  onSave(): void {
    if (this.editGroupForm.invalid) {
      this.editGroupForm.markAllAsTouched();
      return;
    }

    const formValues = this.editGroupForm.value;

    const updatedGroup: Group = {
      ...this.data.group,
      ...formValues
    };

    this.dialogRef.close({
      group: updatedGroup,
      file: this.selectedFile,
      clearPhoto: this.shouldClearPhoto
    });
  }
}
