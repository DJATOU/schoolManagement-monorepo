import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { catchError, throwError } from 'rxjs';
import { CatchUpService } from '../../../services/catch-up.service';
import { StudentService } from '../../student/services/student.service';
import { GroupService } from '../../../services/group.service';
import { LevelService } from '../../../services/level.service';
import { SessionService } from '../../../services/SessionService';
import { Student } from '../../student/domain/student';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { Session } from '../../../models/session/session';
import { StudentAbsence } from '../../../models/catchUp/student-absence';
import { CatchUpRequest } from '../../../models/catchUp/catch-up-request';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

/**
 * Dialogue de création d'une demande de rattrapage (statut PENDING).
 *
 * Filtres en cascade (facultatifs) pour cibler rapidement : Niveau → Groupe → Série →
 * Session. Le niveau et le groupe restreignent la recherche d'étudiant ; le groupe, la
 * série et la session restreignent la liste des séances manquées éligibles. On sélectionne
 * ensuite l'étudiant (autocomplétion) puis l'absence, et l'on crée la demande.
 */
@Component({
  selector: 'app-catch-up-create-dialog',
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
    TranslateModule,
    AdminOnlyDirective
  ],
  template: `
    <h2 mat-dialog-title>{{ 'CATCH_UP.CREATE.TITLE' | translate }}</h2>
    <mat-dialog-content [formGroup]="form" class="create-content">

      <div class="filters">
        <mat-form-field appearance="fill">
          <mat-label>{{ 'CATCH_UP.CREATE.LEVEL' | translate }}</mat-label>
          <mat-select formControlName="levelId" (selectionChange)="onLevelChange()">
            <mat-option [value]="null">{{ 'CATCH_UP.CREATE.ALL' | translate }}</mat-option>
            <mat-option *ngFor="let l of levels" [value]="l.id">{{ l.name }}</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>{{ 'CATCH_UP.CREATE.GROUP' | translate }}</mat-label>
          <mat-select formControlName="groupId" (selectionChange)="onGroupChange()">
            <mat-option [value]="null">{{ 'CATCH_UP.CREATE.ALL' | translate }}</mat-option>
            <mat-option *ngFor="let g of filteredGroups" [value]="g.id">{{ g.name }}</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>{{ 'CATCH_UP.CREATE.SERIES' | translate }}</mat-label>
          <mat-select formControlName="seriesId" (selectionChange)="onSeriesChange()" [disabled]="!form.value.groupId">
            <mat-option [value]="null">{{ 'CATCH_UP.CREATE.ALL' | translate }}</mat-option>
            <mat-option *ngFor="let s of series" [value]="s.id">{{ s.name }}</mat-option>
          </mat-select>
        </mat-form-field>

        <mat-form-field appearance="fill">
          <mat-label>{{ 'CATCH_UP.CREATE.SESSION_FILTER' | translate }}</mat-label>
          <mat-select formControlName="sessionId" (selectionChange)="applyAbsenceFilters()" [disabled]="!form.value.seriesId">
            <mat-option [value]="null">{{ 'CATCH_UP.CREATE.ALL' | translate }}</mat-option>
            <mat-option *ngFor="let s of sessions" [value]="s.id">
              {{ s.title || ('CATCH_UP.CREATE.SESSION' | translate) }}
              <ng-container *ngIf="s.sessionTimeStart"> — {{ s.sessionTimeStart | date:'mediumDate' }}</ng-container>
            </mat-option>
          </mat-select>
        </mat-form-field>
      </div>

      <mat-form-field appearance="fill">
        <mat-label>{{ 'CATCH_UP.CREATE.STUDENT' | translate }}</mat-label>
        <mat-select formControlName="studentId" (selectionChange)="onStudentSelected($event.value)">
          <mat-option *ngFor="let s of filteredStudents" [value]="s.id">
            {{ s.lastName }} {{ s.firstName }}
          </mat-option>
        </mat-select>
        <mat-hint *ngIf="filteredStudents.length === 0">{{ 'CATCH_UP.CREATE.NO_STUDENT' | translate }}</mat-hint>
      </mat-form-field>

      <mat-form-field appearance="fill">
        <mat-label>{{ 'CATCH_UP.CREATE.ABSENCE' | translate }}</mat-label>
        <mat-select formControlName="attendanceId">
          <mat-option *ngFor="let a of filteredAbsences" [value]="a.attendanceId">
            {{ a.sessionTitle || ('CATCH_UP.CREATE.SESSION' | translate) }}
            <ng-container *ngIf="a.sessionDate"> — {{ a.sessionDate | date:'mediumDate' }}</ng-container>
            <ng-container *ngIf="a.groupName"> ({{ a.groupName }})</ng-container>
          </mat-option>
        </mat-select>
      </mat-form-field>

      <p *ngIf="studentSelected && filteredAbsences.length === 0" class="empty-hint">
        {{ 'CATCH_UP.CREATE.NO_ABSENCE' | translate }}
      </p>

      <mat-form-field appearance="fill">
        <mat-label>{{ 'CATCH_UP.CREATE.NOTES' | translate }}</mat-label>
        <textarea matInput formControlName="notes"></textarea>
      </mat-form-field>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'COMMON.CANCEL' | translate }}</button>
      <button mat-raised-button color="primary" (click)="create()" [disabled]="form.invalid" appAdminOnly>
        {{ 'CATCH_UP.CREATE.SUBMIT' | translate }}
      </button>
    </mat-dialog-actions>
  `,
  styles: [
    `.create-content { display: flex; flex-direction: column; gap: 12px; min-width: 380px; }
     .filters { display: grid; grid-template-columns: 1fr 1fr; gap: 8px 12px; }
     .empty-hint { font-size: 12px; color: #b00020; margin: -4px 0 4px; }
    `
  ]
})
export class CatchUpCreateDialogComponent implements OnInit {
  form: FormGroup;

  levels: Level[] = [];
  groups: Group[] = [];
  filteredGroups: Group[] = [];
  series: SessionSeries[] = [];
  sessions: Session[] = [];

  private allStudents: Student[] = [];
  filteredStudents: Student[] = [];

  private absences: StudentAbsence[] = [];
  filteredAbsences: StudentAbsence[] = [];
  studentSelected = false;

  constructor(
    private fb: FormBuilder,
    private catchUpService: CatchUpService,
    private studentService: StudentService,
    private groupService: GroupService,
    private levelService: LevelService,
    private sessionService: SessionService,
    private snackBar: MatSnackBar,
    private dialogRef: MatDialogRef<CatchUpCreateDialogComponent>
  ) {
    this.form = this.fb.group({
      levelId: [null],
      groupId: [null],
      seriesId: [null],
      sessionId: [null],
      studentId: [null, Validators.required],
      attendanceId: [null, Validators.required],
      notes: ['']
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
      },
      error: () => (this.groups = [])
    });
    this.studentService.getStudents().subscribe({
      next: students => {
        this.allStudents = students || [];
        this.setStudentSource(this.allStudents);
      },
      error: () => this.snackBar.open('Impossible de charger les étudiants', 'Fermer', { duration: 4000 })
    });
  }

  // ---- Filtres en cascade -------------------------------------------------

  onLevelChange(): void {
    const levelId = this.form.value.levelId;
    this.filteredGroups = levelId ? this.groups.filter(g => g.levelId === levelId) : this.groups;
    this.form.patchValue({ groupId: null, seriesId: null, sessionId: null });
    this.series = [];
    this.sessions = [];
    // Restreint le vivier d'étudiants par niveau (si aucun groupe précis).
    const pool = levelId ? this.allStudents.filter(s => s.levelId === levelId) : this.allStudents;
    this.setStudentSource(pool);
    this.applyAbsenceFilters();
  }

  onGroupChange(): void {
    const groupId = this.form.value.groupId;
    this.form.patchValue({ seriesId: null, sessionId: null });
    this.series = [];
    this.sessions = [];
    if (groupId) {
      // Étudiants du groupe pour l'autocomplétion.
      this.groupService.getStudentsByGroupId(groupId).subscribe({
        next: students => this.setStudentSource(students || []),
        error: () => this.setStudentSource([])
      });
      // Séries du groupe pour le filtre suivant.
      this.groupService.getSeriesByGroupId(groupId).subscribe({
        next: series => (this.series = series || []),
        error: () => (this.series = [])
      });
    } else {
      const levelId = this.form.value.levelId;
      const pool = levelId ? this.allStudents.filter(s => s.levelId === levelId) : this.allStudents;
      this.setStudentSource(pool);
    }
    this.applyAbsenceFilters();
  }

  onSeriesChange(): void {
    const seriesId = this.form.value.seriesId;
    this.form.patchValue({ sessionId: null });
    this.sessions = [];
    if (seriesId) {
      this.sessionService.getSessionsBySeriesId(seriesId).subscribe({
        next: sessions => (this.sessions = sessions || []),
        error: () => (this.sessions = [])
      });
    }
    this.applyAbsenceFilters();
  }

  // ---- Étudiant / absences ------------------------------------------------

  private setStudentSource(list: Student[]): void {
    this.filteredStudents = [...list].sort((a, b) =>
      `${a.lastName} ${a.firstName}`.localeCompare(`${b.lastName} ${b.firstName}`));
    // Une sélection précédente peut ne plus être dans la nouvelle liste : on réinitialise.
    this.form.patchValue({ studentId: null, attendanceId: null });
    this.studentSelected = false;
    this.absences = [];
    this.filteredAbsences = [];
  }

  onStudentSelected(studentId: number): void {
    this.studentSelected = true;
    this.absences = [];
    this.filteredAbsences = [];
    this.form.patchValue({ attendanceId: null });
    if (studentId == null) {
      return;
    }
    this.catchUpService.getEligibleAbsences(studentId)
      .pipe(
        catchError(error => {
          this.snackBar.open('Impossible de charger les absences', 'Fermer', { duration: 4000 });
          return throwError(() => error);
        })
      )
      .subscribe(absences => {
        this.absences = absences;
        this.applyAbsenceFilters();
      });
  }

  /** Applique les filtres groupe/série/session à la liste des absences éligibles. */
  applyAbsenceFilters(): void {
    const { groupId, seriesId, sessionId } = this.form.value;
    this.filteredAbsences = this.absences.filter(a =>
      (!groupId || a.groupId === groupId) &&
      (!seriesId || a.seriesId === seriesId) &&
      (!sessionId || a.sessionId === sessionId));
    // Si l'absence choisie n'est plus dans la liste filtrée, on la réinitialise.
    const currentAttendanceId = this.form.value.attendanceId;
    if (currentAttendanceId && !this.filteredAbsences.some(a => a.attendanceId === currentAttendanceId)) {
      this.form.patchValue({ attendanceId: null });
    }
  }

  create(): void {
    const { studentId, attendanceId, notes } = this.form.value;
    const absence = this.absences.find(a => a.attendanceId === attendanceId);
    if (!absence) {
      return;
    }
    const request: Partial<CatchUpRequest> = {
      studentId,
      originalSessionId: absence.sessionId,
      originalGroupId: absence.groupId,
      originalAttendanceId: absence.attendanceId,
      notes
    };
    this.catchUpService.createCatchUpRequest(request)
      .pipe(
        catchError(error => {
          const msg = error?.error?.message || 'Impossible de créer la demande de rattrapage';
          this.snackBar.open(msg, 'Fermer', { duration: 5000 });
          return throwError(() => error);
        })
      )
      .subscribe(created => {
        this.snackBar.open('Demande de rattrapage créée', 'Fermer', { duration: 3000 });
        this.dialogRef.close(created);
      });
  }
}
