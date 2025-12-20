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
      box-shadow: 0 1px 3px rgba(0, 0, 0, 0.1);
      border-radius: 8px;
      overflow: hidden;
    }

    ::ng-deep .view-toggle .mat-button-toggle {
      border: none;
    }

    ::ng-deep .view-toggle .mat-button-toggle-button {
      display: flex;
      align-items: center;
      gap: 0.5rem;
      padding: 0.5rem 1rem;
      font-size: 0.875rem;
      font-weight: 500;
    }

    ::ng-deep .view-toggle .mat-button-toggle-checked {
      background-color: rgba(59, 130, 246, 0.1);
      color: #3b82f6;
    }

    ::ng-deep .view-toggle mat-icon {
      font-size: 1.25rem;
      width: 1.25rem;
      height: 1.25rem;
    }

    @media (max-width: 600px) {
      ::ng-deep .view-toggle .mat-button-toggle-button span {
        display: none;
      }
      
      ::ng-deep .view-toggle .mat-button-toggle-button {
        padding: 0.5rem;
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
