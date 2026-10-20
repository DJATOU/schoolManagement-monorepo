import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, tap, throwError } from 'rxjs';
import { CatchUpService } from '../../../services/catch-up.service';
import { Session } from '../../../models/session/session';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { LocalizedDatePipe } from '../../../pipes/localized-date.pipe';

/** Données attendues par le dialogue de planification. */
export interface CatchUpScheduleData {
  requestId: number;
  studentId: number;
  originalSessionId: number;
  originalSessionName?: string;
  originalGroupId?: number;
}

/**
 * Dialogue de planification d'une demande de rattrapage EXISTANTE (statut PENDING).
 *
 * On choisit une séance de rattrapage parmi les séances compatibles (même année, même type
 * de groupe, même prix). Le groupe de rattrapage est déduit automatiquement de la séance
 * choisie. La demande passe alors à l'état SCHEDULED.
 */
@Component({
  selector: 'app-catch-up-dialog',
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
    AdminOnlyDirective,
    TranslateModule,
    LocalizedDatePipe
  ],
  template: `
    <h2 mat-dialog-title>{{ 'CATCH_UP.SCHEDULE.TITLE' | translate }}</h2>
    <mat-dialog-content [formGroup]="catchUpForm" class="catch-up-content">
      <div class="session-info">
        <p><strong>{{ 'CATCH_UP.SCHEDULE.MISSED_SESSION' | translate }}</strong>
          {{ data.originalSessionName || ('#' + data.originalSessionId) }}</p>
      </div>
      <mat-form-field appearance="fill">
        <mat-label>{{ 'CATCH_UP.SCHEDULE.CATCH_UP_SESSION' | translate }}</mat-label>
        <mat-select formControlName="catchUpSessionId" (selectionChange)="onSessionChange($event.value)">
          <mat-option *ngFor="let session of availableSessions" [value]="session.id">
            {{ session.title || ('CATCH_UP.SCHEDULE.SESSION_FALLBACK' | translate) }}
            <ng-container *ngIf="session.sessionTimeStart"> - {{ session.sessionTimeStart | appDate:'mediumDate' }}</ng-container>
            <ng-container *ngIf="session.groupName"> ({{ session.groupName }})</ng-container>
          </mat-option>
        </mat-select>
      </mat-form-field>
      <p *ngIf="loaded && availableSessions.length === 0" class="empty-hint">
        {{ 'CATCH_UP.SCHEDULE.NO_SESSION' | translate }}
      </p>
      <mat-form-field appearance="fill">
        <mat-label>{{ 'CATCH_UP.CREATE.NOTES' | translate }}</mat-label>
        <textarea matInput formControlName="notes"></textarea>
      </mat-form-field>
      <div class="helper-text">
        <p>{{ 'CATCH_UP.SCHEDULE.PAYMENT_HINT' | translate }}</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>{{ 'common.cancel' | translate }}</button>
      <button mat-raised-button color="primary" (click)="scheduleCatchUp()" [disabled]="catchUpForm.invalid" appAdminOnly>{{ 'CATCH_UP.LIST.SCHEDULE' | translate }}</button>
    </mat-dialog-actions>
  `,
  styles: [
    `.catch-up-content { display: flex; flex-direction: column; gap: 12px; min-width: 360px; }
     .helper-text { font-size: 12px; color: #666; }
     .empty-hint { font-size: 12px; color: #b00020; margin: -4px 0 4px; }
     .session-info { background: #f5f5f5; padding: 8px; border-radius: 4px; }
    `
  ]
})
export class CatchUpDialogComponent implements OnInit {
  catchUpForm: FormGroup;
  availableSessions: Session[] = [];
  loaded = false;
  private catchUpGroupId: number | null = null;

  constructor(
    private fb: FormBuilder,
    private catchUpService: CatchUpService,
    private snackBar: MatSnackBar,
    private translate: TranslateService,
    public dialogRef: MatDialogRef<CatchUpDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: CatchUpScheduleData
  ) {
    this.catchUpForm = this.fb.group({
      catchUpSessionId: [null, Validators.required],
      notes: ['']
    });
  }

  ngOnInit(): void {
    if (this.data.studentId && this.data.originalSessionId) {
      this.loadAvailableSessions();
    }
  }

  private loadAvailableSessions(): void {
    this.catchUpService
      .getAvailableSessions(this.data.studentId, this.data.originalSessionId)
      .pipe(
        tap(() => (this.loaded = true)),
        catchError(error => {
          this.loaded = true;
          console.error('Erreur lors du chargement des sessions disponibles', error);
          this.snackBar.open(this.translate.instant('CATCH_UP.SCHEDULE.LOAD_ERROR'), this.translate.instant('common.close'), { duration: 4000 });
          return throwError(() => error);
        })
      )
      .subscribe(sessions => (this.availableSessions = sessions));
  }

  onSessionChange(sessionId: number): void {
    const session = this.availableSessions.find(s => s.id === sessionId);
    this.catchUpGroupId = session?.groupId ?? null;
  }

  scheduleCatchUp(): void {
    const { catchUpSessionId } = this.catchUpForm.value;
    if (this.catchUpGroupId == null) {
      const session = this.availableSessions.find(s => s.id === catchUpSessionId);
      this.catchUpGroupId = session?.groupId ?? null;
    }
    if (this.catchUpGroupId == null) {
      this.snackBar.open(this.translate.instant('CATCH_UP.SCHEDULE.GROUP_NOT_FOUND'), this.translate.instant('common.close'), { duration: 4000 });
      return;
    }

    this.catchUpService
      .scheduleCatchUp(this.data.requestId, catchUpSessionId, this.catchUpGroupId)
      .pipe(
        catchError(error => {
          const msg = error?.error?.message || this.translate.instant('CATCH_UP.SCHEDULE.SCHEDULE_ERROR');
          this.snackBar.open(msg, this.translate.instant('common.close'), { duration: 5000 });
          return throwError(() => error);
        })
      )
      .subscribe(response => {
        this.snackBar.open(this.translate.instant('CATCH_UP.SCHEDULE.SCHEDULED'), this.translate.instant('common.close'), { duration: 3000 });
        this.dialogRef.close(response);
      });
  }
}
