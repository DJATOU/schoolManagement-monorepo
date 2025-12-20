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
      padding: 0.75rem 0;
      margin-bottom: 1rem;
      border-bottom: 1px solid #e5e7eb;
    }

    .summary {
      display: flex;
      align-items: baseline;
      gap: 0.5rem;
    }

    .count {
      font-size: 1.5rem;
      font-weight: 600;
      color: #0f172a;
    }

    .label {
      font-size: 0.875rem;
      color: #6b7280;
      text-transform: lowercase;
    }

    .late-count {
      font-size: 0.875rem;
      color: #ef4444;
      font-weight: 600;
      margin-left: 0.5rem;
    }

    .quick-filter {
      font-size: 0.875rem;
    }

    ::ng-deep .quick-filter .mat-slide-toggle-label {
      font-weight: 500;
    }

    @media (max-width: 600px) {
      .list-header {
        flex-direction: column;
        align-items: flex-start;
        gap: 0.75rem;
      }

      .count {
        font-size: 1.25rem;
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
