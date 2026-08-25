import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { DiscountService } from '../../../services/discount.service';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { DiscountRequest } from '../../../models/discount/student-discount';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { Student } from '../../student/domain/student';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/**
 * Dialog d'ajout d'une réduction, ergonomique et aligné sur le backend.
 *
 * Flux : Niveau (filtre facultatif) → Groupe (par nom) → Étudiant (par nom, chargé
 * depuis le groupe) → Taux en %. La réduction est enregistrée avec la portée GROUP
 * (réduction de l'élève sur ce groupe), le taux étant converti en décimal [0,1].
 */
@Component({
  selector: 'app-discount-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatSnackBarModule,
    AdminOnlyDirective
  ],
  template: `
    <h2 mat-dialog-title>Ajouter une réduction</h2>
    <mat-dialog-content [formGroup]="discountForm" class="discount-content">
      <mat-form-field appearance="fill">
        <mat-label>Niveau (filtre)</mat-label>
        <mat-select formControlName="levelId" (selectionChange)="onLevelChange()">
          <mat-option [value]="null">Tous les niveaux</mat-option>
          <mat-option *ngFor="let l of levels" [value]="l.id">{{ l.name }}</mat-option>
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="fill">
        <mat-label>Groupe</mat-label>
        <mat-select formControlName="groupId" (selectionChange)="onGroupChange()">
          <mat-option *ngFor="let g of filteredGroups" [value]="g.id">{{ g.name }}</mat-option>
        </mat-select>
      </mat-form-field>

      <mat-form-field appearance="fill">
        <mat-label>Étudiant</mat-label>
        <mat-select formControlName="studentId">
          <mat-option *ngIf="loadingStudents" [value]="null" disabled>Chargement...</mat-option>
          <mat-option *ngFor="let s of students" [value]="s.id">
            {{ s.firstName }} {{ s.lastName }}
          </mat-option>
        </mat-select>
        <mat-hint *ngIf="!discountForm.value.groupId">Choisissez d'abord un groupe</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="fill">
        <mat-label>Taux (%)</mat-label>
        <input matInput type="number" min="0" max="100" formControlName="ratePercent" />
        <mat-hint>0 à 100 (ex. 50 = 50%)</mat-hint>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" (click)="save()" [disabled]="discountForm.invalid" appAdminOnly>Enregistrer</button>
    </mat-dialog-actions>
  `,
  styles: [`.discount-content { display: grid; gap: 12px; min-width: 340px; }`]
})
export class DiscountDialogComponent implements OnInit {
  discountForm: FormGroup;

  levels: Level[] = [];
  groups: Group[] = [];
  filteredGroups: Group[] = [];
  students: Student[] = [];
  loadingStudents = false;

  constructor(
    private fb: FormBuilder,
    private discountService: DiscountService,
    private groupService: GroupService,
    private levelService: LevelService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<DiscountDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId?: number; groupId?: number }
  ) {
    this.discountForm = this.fb.group({
      levelId: [null],
      groupId: [data?.groupId || null, Validators.required],
      studentId: [data?.studentId || null, [Validators.required, Validators.min(1)]],
      ratePercent: [null, [Validators.required, Validators.min(0), Validators.max(100)]]
    });
  }

  ngOnInit(): void {
    this.levelService.getLevels().subscribe({
      next: levels => (this.levels = levels || []),
      error: () => (this.levels = [])
    });
    this.groupService.getGroups().subscribe({
      next: groups => {
        this.groups = groups || [];
        this.filteredGroups = this.groups;
        // Si un groupe est pré-sélectionné, charger ses étudiants.
        if (this.discountForm.value.groupId) {
          this.loadStudents(this.discountForm.value.groupId);
        }
      },
      error: () => (this.groups = [])
    });
  }

  onLevelChange(): void {
    const levelId = this.discountForm.value.levelId;
    this.filteredGroups = levelId
      ? this.groups.filter(g => g.levelId === levelId)
      : this.groups;
    // Réinitialise groupe + étudiant.
    this.discountForm.patchValue({ groupId: null, studentId: null });
    this.students = [];
  }

  onGroupChange(): void {
    const groupId = this.discountForm.value.groupId;
    this.discountForm.patchValue({ studentId: null });
    if (groupId) {
      this.loadStudents(groupId);
    } else {
      this.students = [];
    }
  }

  private loadStudents(groupId: number): void {
    this.loadingStudents = true;
    this.groupService.getStudentsByGroupId(groupId).subscribe({
      next: students => {
        this.students = students || [];
        this.loadingStudents = false;
      },
      error: () => {
        this.students = [];
        this.loadingStudents = false;
      }
    });
  }

  save(): void {
    const v = this.discountForm.value;
    const payload: DiscountRequest = {
      studentId: v.studentId,
      scope: 'GROUP',
      groupId: v.groupId,
      seriesId: null,
      sessionId: null,
      rate: Number(v.ratePercent) / 100
    };

    this.discountService.addDiscount(payload).subscribe({
      next: discount => {
        this.snackBar.open('Réduction enregistrée', 'Fermer', { duration: 3000 });
        this.dialogRef.close(discount);
      },
      error: (err: Error) => this.snackBar.open(err.message || 'Erreur lors de la sauvegarde', 'Fermer', { duration: 4000 })
    });
  }
}
