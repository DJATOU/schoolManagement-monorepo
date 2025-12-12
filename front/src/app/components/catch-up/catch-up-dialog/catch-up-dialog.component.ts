import { CommonModule } from '@angular/common';
import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { catchError, switchMap, tap, throwError } from 'rxjs';
import { CatchUpService } from '../../../services/catch-up.service';
import { CatchUpRequest } from '../../../models/catchUp/catch-up-request';
import { Session } from '../../../models/session/session';
import { Attendance } from '../../../models/Attendance/attendance';

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
    MatSnackBarModule
  ],
  template: `
    <h2 mat-dialog-title>Planifier un rattrapage</h2>
    <mat-dialog-content [formGroup]="catchUpForm" class="catch-up-content">
      <div class="session-info" *ngIf="data.attendance">
        <p><strong>Session manquée :</strong> {{ data.attendance.sessionId }} (Groupe {{ data.attendance.groupId }})</p>
      </div>
      <mat-form-field appearance="fill">
        <mat-label>Session de rattrapage</mat-label>
        <mat-select formControlName="catchUpSessionId" (selectionChange)="onSessionChange($event.value)">
          <mat-option *ngFor="let session of availableSessions" [value]="session.id">
            {{ session.title || 'Session' }} - {{ session.sessionTimeStart | date:'mediumDate' }}
          </mat-option>
        </mat-select>
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Groupe de rattrapage</mat-label>
        <input matInput formControlName="catchUpGroupId" type="number" />
      </mat-form-field>
      <mat-form-field appearance="fill">
        <mat-label>Notes</mat-label>
        <textarea matInput formControlName="notes"></textarea>
      </mat-form-field>
      <div class="helper-text">
        <p>Le paiement sera associé uniquement à la session de rattrapage sélectionnée.</p>
      </div>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button mat-dialog-close>Annuler</button>
      <button mat-raised-button color="primary" (click)="scheduleCatchUp()" [disabled]="catchUpForm.invalid">Planifier</button>
    </mat-dialog-actions>
  `,
  styles: [
    `.catch-up-content { display: flex; flex-direction: column; gap: 12px; }
     .helper-text { font-size: 12px; color: #666; }
     .session-info { background: #f5f5f5; padding: 8px; border-radius: 4px; }
    `
  ]
})
export class CatchUpDialogComponent implements OnInit {
  catchUpForm: FormGroup;
  availableSessions: Session[] = [];

  constructor(
    private fb: FormBuilder,
    private catchUpService: CatchUpService,
    private snackBar: MatSnackBar,
    public dialogRef: MatDialogRef<CatchUpDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number; attendance: Attendance }
  ) {
    this.catchUpForm = this.fb.group({
      catchUpSessionId: [null, Validators.required],
      catchUpGroupId: [null, Validators.required],
      notes: ['']
    });
  }

  ngOnInit(): void {
    if (this.data.studentId && this.data.attendance?.sessionId) {
      this.loadAvailableSessions();
    }
  }

  private loadAvailableSessions(): void {
    this.catchUpService
      .getAvailableSessions(this.data.studentId, this.data.attendance.sessionId)
      .pipe(
        tap(() => console.log('Loading available sessions for catch-up')),
        catchError(error => {
          console.error('Erreur lors du chargement des sessions disponibles', error);
          this.snackBar.open('Impossible de charger les sessions de rattrapage', 'Fermer', { duration: 4000 });
          return throwError(() => error);
        })
      )
      .subscribe(sessions => (this.availableSessions = sessions));
  }

  onSessionChange(sessionId: number): void {
    console.log('Session de rattrapage choisie', sessionId);
  }

  scheduleCatchUp(): void {
    const formValue = this.catchUpForm.value;
    const baseRequest: Partial<CatchUpRequest> = {
      studentId: this.data.studentId,
      originalSessionId: this.data.attendance.sessionId,
      originalGroupId: this.data.attendance.groupId,
      originalAttendanceId: this.data.attendance.id,
      requestDate: new Date(),
      status: 'PENDING',
      notes: formValue.notes
    };

    this.catchUpService
      .createCatchUpRequest(baseRequest)
      .pipe(
        switchMap(request =>
          this.catchUpService.scheduleCatchUp(request.id!, formValue.catchUpSessionId, formValue.catchUpGroupId).pipe(
            tap(() => console.log('Catch-up scheduled for request', request.id))
          )
        ),
        catchError(error => {
          console.error('Erreur lors de la planification du rattrapage', error);
          this.snackBar.open('Impossible de planifier le rattrapage', 'Fermer', { duration: 4000 });
          return throwError(() => error);
        })
      )
      .subscribe(response => {
        this.snackBar.open('Rattrapage planifié', 'Fermer', { duration: 3000 });
        this.dialogRef.close(response);
      });
  }
}
