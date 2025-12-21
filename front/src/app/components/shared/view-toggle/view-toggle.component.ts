import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-view-toggle',
  standalone: true,
  imports: [CommonModule, MatButtonToggleModule, MatIconModule],
  template: `
    <mat-button-toggle-group 
      [value]="viewMode"
      (change)="onViewModeChange($event.value)"
      class="view-toggle">
      <mat-button-toggle value="card">
        <mat-icon>view_comfy</mat-icon>
        <span>Grid</span>
      </mat-button-toggle>
      <mat-button-toggle value="list">
        <mat-icon>view_list</mat-icon>
        <span>List</span>
      </mat-button-toggle>
    </mat-button-toggle-group>
  `,
  styles: [`
    .view-toggle {
      background: #f8fafc;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      overflow: hidden;
      padding: 3px;
    }

    ::ng-deep .view-toggle .mat-button-toggle {
      border: none;
      background: transparent;
    }

    ::ng-deep .view-toggle .mat-button-toggle-button {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 14px;
      font-size: 13px;
      font-weight: 600;
      border-radius: 8px;
      transition: all 0.2s ease;
      color: #64748b;
    }

    ::ng-deep .view-toggle .mat-button-toggle-checked .mat-button-toggle-button {
      background: white;
      color: #6366f1;
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.08);
    }

    ::ng-deep .view-toggle .mat-button-toggle:not(.mat-button-toggle-checked) .mat-button-toggle-button:hover {
      background: rgba(255, 255, 255, 0.5);
      color: #475569;
    }

    ::ng-deep .view-toggle mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    @media (max-width: 600px) {
      ::ng-deep .view-toggle .mat-button-toggle-button span {
        display: none;
      }
      
      ::ng-deep .view-toggle .mat-button-toggle-button {
        padding: 6px 10px;
      }
    }
  `]
})
export class ViewToggleComponent {
  @Input() viewMode: 'card' | 'list' = 'card';
  @Output() viewModeChange = new EventEmitter<'card' | 'list'>();

  onViewModeChange(mode: 'card' | 'list'): void {
    this.viewModeChange.emit(mode);
  }
}
