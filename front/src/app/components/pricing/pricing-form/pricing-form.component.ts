import { Component, OnInit, inject } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatDialog } from '@angular/material/dialog';
import { PricingService } from '../../../services/pricing.service';
import { Pricing } from '../../../models/pricing/pricing';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { MatTabsModule } from '@angular/material/tabs';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { provideTranslatedDatepickerIntl } from '../../../shared/translated-datepicker-intl';
import { MatNativeDateModule } from '@angular/material/core';
import { MatIconModule } from '@angular/material/icon';
import { MatOptionModule } from '@angular/material/core';
import { ReactiveFormsModule } from '@angular/forms';
import { CommonModule } from '@angular/common';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';

@Component({
  selector: 'app-pricing-form',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    MatFormFieldModule,
    MatInputModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatOptionModule,
    MatTabsModule,
    MatSnackBarModule,
    CommonModule,
    MatCardModule,
    AdminOnlyDirective,
    TranslateModule
  ],
  templateUrl: './pricing-form.component.html',
  styleUrls: ['./pricing-form.component.scss'],
  providers: [provideTranslatedDatepickerIntl(), PricingService]
})
export class PricingFormComponent implements OnInit {
  pricingForm!: FormGroup;

  /** Identifiant du tarif en cours de modification (null en mode création). */
  editingId: number | null = null;

  private readonly translate = inject(TranslateService);

  constructor(
    private fb: FormBuilder,
    private pricingService: PricingService,
    public dialog: MatDialog,
    private snackBar: MatSnackBar,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.pricingForm = this.fb.group({
      price: ['', [Validators.required, Validators.min(0)]],
      effectiveDate: ['', Validators.required],
      expirationDate: ['', Validators.required],
      description: ['']
    });

    // Mode modification : un identifiant est présent dans l'URL (pricing/edit/:id).
    const idParam = this.route.snapshot.paramMap.get('id');
    if (idParam) {
      this.editingId = Number(idParam);
      this.pricingService.getPricingById(this.editingId).subscribe({
        next: (pricing) => this.pricingForm.patchValue({
          price: pricing.price ?? '',
          effectiveDate: pricing.effectiveDate ?? '',
          expirationDate: pricing.expirationDate ?? '',
          description: pricing.description ?? ''
        }),
        error: () => this.showErrorMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.pricing.LOAD_ERROR'))
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
    if (this.pricingForm.valid) {
      const formData = {
        basicInformation: {
          price: this.pricingForm.get('price')?.value,
          effectiveDate: this.pricingForm.get('effectiveDate')?.value,
          expirationDate: this.pricingForm.get('expirationDate')?.value,
          description: this.pricingForm.get('description')?.value
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
          const pricing: Pricing = {
            price: formData.basicInformation.price ?? 0,
            effectiveDate: formData.basicInformation.effectiveDate ? new Date(formData.basicInformation.effectiveDate) : new Date(),
            expirationDate: formData.basicInformation.expirationDate ? new Date(formData.basicInformation.expirationDate) : new Date(),
            description: formData.basicInformation.description ?? ''
          };

          if (this.editingId) {
            // Mode modification : mise à jour du tarif existant.
            this.pricingService.updatePricing(this.editingId, pricing).subscribe({
              next: () => {
                this.showSuccessMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.pricing.UPDATED'));
                this.router.navigate(['/pricing/table']);
              },
              error: (error) => {
                console.error('Error updating pricing:', error);
                this.showErrorMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.pricing.UPDATE_ERROR'));
              }
            });
          } else {
            this.pricingService.createPricing(pricing).subscribe({
              next: (created) => {
                console.log('Pricing created:', created);
                this.onClearForm();
                this.showSuccessMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.pricing.CREATED'));
              },
              error: (error) => {
                console.error('Error creating pricing:', error);
                this.showErrorMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.pricing.CREATE_ERROR'));
              }
            });
          }
        } else {
          console.warn('Form submission was cancelled.');
        }
      });
    } else {
      console.warn('Form is not valid');
      this.showErrorMessage(this.translate.instant('RESOURCE_FORM.MESSAGES.FORM_INVALID'));
    }
  }

  onClearForm(): void {
    this.pricingForm.reset();
  }

  showSuccessMessage(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, this.translate.instant('common.ok'), {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }
}
