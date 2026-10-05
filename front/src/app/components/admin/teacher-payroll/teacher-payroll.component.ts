import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatIconModule } from '@angular/material/icon';
import { MatTabsModule } from '@angular/material/tabs';
import { TranslateModule } from '@ngx-translate/core';
import { PaidTabComponent } from './paid-tab/paid-tab.component';
import { PayableTabComponent } from './payable-tab/payable-tab.component';
import { RatesTabComponent } from './rates-tab/rates-tab.component';

/**
 * Page « Paie des enseignants » (spec teacher-payroll, exigence 9.1), réservée au rôle ADMIN : trois
 * onglets, À payer, Paies versées, Taux.
 *
 * <p>Chaque onglet est recréé à son ouverture (`matTabContent`) : une paie enregistrée dans « À
 * payer » figure donc dans « Paies versées » dès qu'on y passe, et un taux créé est proposé à la
 * paie suivante.</p>
 */
@Component({
  selector: 'app-teacher-payroll',
  standalone: true,
  imports: [CommonModule, MatIconModule, MatTabsModule, TranslateModule, PayableTabComponent, PaidTabComponent,
    RatesTabComponent],
  template: `
    <div class="page-container">
      <div class="header">
        <h1>{{ 'teacherPayroll.title' | translate }}</h1>
        <p>{{ 'teacherPayroll.subtitle' | translate }}</p>
      </div>
      <mat-tab-group animationDuration="0ms" mat-stretch-tabs="false">
        <mat-tab>
          <ng-template mat-tab-label><mat-icon class="tab-icon">pending_actions</mat-icon>{{ 'teacherPayroll.tabs.payable' | translate }}</ng-template>
          <ng-template matTabContent><app-payable-tab></app-payable-tab></ng-template>
        </mat-tab>
        <mat-tab>
          <ng-template mat-tab-label><mat-icon class="tab-icon">receipt_long</mat-icon>{{ 'teacherPayroll.tabs.paid' | translate }}</ng-template>
          <ng-template matTabContent><app-paid-tab></app-paid-tab></ng-template>
        </mat-tab>
        <mat-tab>
          <ng-template mat-tab-label><mat-icon class="tab-icon">percent</mat-icon>{{ 'teacherPayroll.tabs.rates' | translate }}</ng-template>
          <ng-template matTabContent><app-rates-tab></app-rates-tab></ng-template>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .page-container { padding: 24px; max-width: 1400px; margin: 0 auto; }
    .header h1 { margin: 0; font-size: 1.6rem; font-weight: 700; color: #1e293b; }
    .header p { margin: 4px 0 16px; color: #64748b; }
    .tab-icon { margin-right: 8px; }
  `]
})
export class TeacherPayrollComponent {
}
