import { HttpClientModule } from '@angular/common/http';
import { Component, OnInit, ViewEncapsulation } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { Level } from '../../../models/level/level';
import { LevelService } from '../../../services/level.service';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { MatTab, MatTabGroup } from '@angular/material/tabs';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { TranslateModule } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-level-form',
  standalone: true,
  imports: [
    ReactiveFormsModule, 
    MatFormFieldModule, 
    MatInputModule,
    
// TODO: `HttpClientModule` should not be imported into a component directly.
// Please refactor the code to add `provideHttpClient()` call to the provider list in the
// application bootstrap logic and remove the `HttpClientModule` import from this component.
HttpClientModule,
    RouterModule,
    MatSnackBarModule,
    MatTab,
    MatTabGroup,
    AdminOnlyDirective,
    TranslateModule
  ],
  templateUrl: './level-form.component.html',
  styleUrls: ['./level-form.component.scss'],
  encapsulation: ViewEncapsulation.None 
})
export class LevelFormComponent implements OnInit {

  /** Identifiant du niveau en cours d'édition (null en mode création). */
  editingId: number | null = null;

  levelForm = this.fb.group({
    name: ['', Validators.required],
    levelCode: ['', Validators.required],
    levelSequence: [null as number | null, [Validators.required, Validators.min(1)]],
    description: ['']
  });

  constructor(private fb: FormBuilder,
    private levelService: LevelService,
    public dialog: MatDialog,
    private snackBar: MatSnackBar,
    private route: ActivatedRoute,
    private router: Router) { }

  ngOnInit(): void {
    // Mode édition : un identifiant est présent dans l'URL (level/edit/:id).
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editingId = Number(idParam);
      this.levelService.getLevelById(this.editingId).subscribe({
        next: (level) => {
          this.levelForm.patchValue({
            name: level.name ?? '',
            levelCode: level.levelCode ?? '',
            levelSequence: level.levelSequence ?? null,
            description: level.description ?? ''
          });
        },
        error: () => this.showErrorMessage('Erreur lors du chargement du niveau.')
      });
    }
  }

  flattenFormData(data: any, parentKey: string = ''): { label: string, value: any }[] {
    let result: { label: string, value: any }[] = [];
    Object.keys(data).forEach(key => {
      const newKey = parentKey ? `${parentKey} - ${key}` : key;
      const value = data[key];
      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        result = result.concat(this.flattenFormData(value, newKey));
      } else if (Array.isArray(value)) {
        result.push({ label: newKey, value: value.join(', ') });
      } else {
        result.push({ label: newKey, value: value });
      }
    });
    return result;
  }

  onSubmit(): void {
    if (this.levelForm.valid) {
      const formData = {
        basicInformation: {
          name: this.levelForm.get('name')?.value,
          levelCode: this.levelForm.get('levelCode')?.value,
          levelSequence: this.levelForm.get('levelSequence')?.value,
          description: this.levelForm.get('description')?.value
        }
      };

      const flattenedData = this.flattenFormData(formData);
      console.log('Form Data:', formData);
      console.log('Flattened Data:', flattenedData);

      const dialogRef = this.dialog.open(SummaryDialogComponent, {
        data: flattenedData
      });

      dialogRef.afterClosed().subscribe(result => {
        if (result) {
          const level: Level = {
            name: formData.basicInformation.name ?? '',
            levelCode: formData.basicInformation.levelCode ?? '',
            levelSequence: formData.basicInformation.levelSequence ?? undefined,
            description: formData.basicInformation.description ?? ''
          };

          if (this.editingId) {
            // Mode édition : mise à jour du niveau existant (dont son rang).
            this.levelService.updateLevel(this.editingId, level).subscribe({
              next: () => {
                this.showSuccessMessage('Niveau mis à jour avec succès.');
                this.router.navigate(['/level/table']);
              },
              error: (error) => {
                console.error('Error updating level:', error);
                this.showErrorMessage('Erreur lors de la mise à jour du niveau.');
              }
            });
          } else {
            this.levelService.createLevel(level).subscribe({
              next: (created) => {
                console.log('Level created:', created);
                this.showSuccessMessage('Level created successfully.');
                this.onClearForm();
              },
              error: (error) => {
                console.error('Error creating level:', error);
                this.showErrorMessage('Error creating level.');
              }
            });
          }
        } else {
          console.warn('Form submission was cancelled.');
        }
      });
    } else {
      console.warn('Form is not valid');
      this.showErrorMessage('The form is not valid.');
    }
  }

  onClearForm() {
    this.levelForm.reset();
  }

  showSuccessMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }
}
