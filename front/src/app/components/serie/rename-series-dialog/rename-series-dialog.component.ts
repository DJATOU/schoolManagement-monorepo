import { Component, Inject } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslateModule } from '@ngx-translate/core';

/** Données attendues par le dialogue : le nom actuel de la série. */
export interface RenameSeriesDialogData {
  currentName: string;
}

/**
 * Dialogue de renommage d'une série.
 *
 * <p>Renvoie le nouveau nom (chaîne) à la fermeture, ou {@code undefined} si l'utilisateur
 * annule. Le nom est retourné détrimé ; le serveur reste l'autorité sur la validation.</p>
 */
@Component({
  selector: 'app-rename-series-dialog',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    TranslateModule
  ],
  template: `
    <h2 mat-dialog-title>{{ 'series.rename.title' | translate }}</h2>

    <form [formGroup]="form" (ngSubmit)="onSubmit()">
      <mat-dialog-content>
        <p class="rename-hint">{{ 'series.rename.hint' | translate }}</p>

        <mat-form-field appearance="fill" class="rename-field">
          <mat-label>{{ 'series.rename.label' | translate }}</mat-label>
          <input matInput formControlName="name" maxlength="255" required cdkFocusInitial>
          <mat-error *ngIf="form.get('name')?.hasError('required')">
            {{ 'series.rename.errors.required' | translate }}
          </mat-error>
        </mat-form-field>
      </mat-dialog-content>

      <mat-dialog-actions align="end">
        <button mat-button type="button" (click)="onCancel()">
          {{ 'common.cancel' | translate }}
        </button>
        <button mat-raised-button color="primary" type="submit" [disabled]="form.invalid">
          {{ 'common.confirm' | translate }}
        </button>
      </mat-dialog-actions>
    </form>
  `,
  styles: [`
    .rename-field { width: 100%; }
    .rename-hint { margin: 0 0 12px; color: #64748b; font-size: 13px; }
  `]
})
export class RenameSeriesDialogComponent {
  form: FormGroup;

  constructor(
    private fb: FormBuilder,
    private dialogRef: MatDialogRef<RenameSeriesDialogComponent, string | undefined>,
    @Inject(MAT_DIALOG_DATA) public data: RenameSeriesDialogData
  ) {
    this.form = this.fb.group({
      // Un nom composé uniquement d'espaces est refusé aussi bien ici que côté serveur.
      name: [data.currentName ?? '', [Validators.required, Validators.maxLength(255)]]
    });
  }

  onSubmit(): void {
    const name = (this.form.get('name')!.value ?? '').trim();
    if (!name) {
      this.form.get('name')!.setErrors({ required: true });
      return;
    }
    // Nom inchangé : on ferme sans déclencher d'appel réseau inutile.
    if (name === (this.data.currentName ?? '').trim()) {
      this.dialogRef.close(undefined);
      return;
    }
    this.dialogRef.close(name);
  }

  onCancel(): void {
    this.dialogRef.close(undefined);
  }
}
