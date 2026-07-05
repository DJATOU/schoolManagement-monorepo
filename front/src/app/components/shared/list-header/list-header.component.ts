import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

@Component({
  selector: 'app-list-header',
  standalone: true,
  imports: [CommonModule, MatSlideToggleModule, FormsModule],
  template: `
    <div class="list-header">
      <div class="summary">
        <span class="count">{{ count }}</span>
        <span class="label">{{ entityName }}</span>
        <span class="late-count" *ngIf="lateCount > 0">• Retards: {{ lateCount }}</span>
      </div>
      
      <mat-slide-toggle
        *ngIf="filterLabel"
        [(ngModel)]="filterActive"
        (change)="onFilterChange()"
        class="quick-filter">
        {{ filterLabel }}
      </mat-slide-toggle>
    </div>
  `,
  styles: [`
    .list-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      padding: 0;
      gap: 1rem;
    }

    .summary {
      display: flex;
      align-items: baseline;
      gap: 0.375rem;
    }

    .count {
      font-size: 1.25rem;
      font-weight: 700;
      color: #0f172a;
    }

    .label {
      font-size: 0.8125rem;
      color: #64748b;
      text-transform: lowercase;
    }

    .late-count {
      font-size: 0.8125rem;
      color: #ef4444;
      font-weight: 600;
      margin-left: 0.5rem;
      padding: 2px 8px;
      background: #fef2f2;
      border-radius: 10px;
    }

    .quick-filter {
      font-size: 0.8125rem;
    }

    ::ng-deep .quick-filter .mdc-form-field {
      font-size: 0.8125rem;
    }

    ::ng-deep .quick-filter .mdc-label {
      font-weight: 500;
      color: #475569;
    }

    @media (max-width: 600px) {
      .list-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.5rem;
      }

      .count {
        font-size: 1.125rem;
      }
    }
  `]
})
export class ListHeaderComponent {
  @Input() count: number = 0;
  @Input() entityName: string = 'items';
  @Input() filterLabel?: string;
  @Input() lateCount: number = 0;
  @Output() filterChange = new EventEmitter<boolean>();

  filterActive: boolean = false;

  onFilterChange(): void {
    this.filterChange.emit(this.filterActive);
  }
}
