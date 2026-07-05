import { Component, EventEmitter, Input, Output } from '@angular/core';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { CommonModule } from '@angular/common';

@Component({
  selector: 'app-view-toggle',
  standalone: true,
  imports: [CommonModule, MatButtonToggleModule, MatIconModule, MatTooltipModule],
  template: `
    <mat-button-toggle-group 
      [value]="viewMode"
      (change)="onViewModeChange($event.value)"
      class="view-toggle"
      aria-label="Mode d'affichage">
      <mat-button-toggle value="card" matTooltip="Affichage en cartes" aria-label="Cartes">
        <mat-icon>grid_view</mat-icon>
        <span>Cartes</span>
      </mat-button-toggle>
      <mat-button-toggle value="list" matTooltip="Affichage en liste" aria-label="Liste">
        <mat-icon>view_list</mat-icon>
        <span>Liste</span>
      </mat-button-toggle>
    </mat-button-toggle-group>
  `,
  styles: [`
    .view-toggle {
      background: #f1f5f9;
      border: 1px solid #e2e8f0;
      border-radius: 10px;
      overflow: hidden;
      padding: 3px;
      gap: 2px;
    }

    ::ng-deep .view-toggle .mat-button-toggle {
      border: none !important;
      background: transparent;
    }

    ::ng-deep .view-toggle .mat-button-toggle-button {
      display: flex;
      align-items: center;
      gap: 6px;
      padding: 6px 16px;
      font-size: 13px;
      font-weight: 600;
      border-radius: 8px;
      transition: all 0.2s ease;
      color: #64748b;
    }

    ::ng-deep .view-toggle .mat-button-toggle-checked .mat-button-toggle-button {
      background: #6366f1;
      color: #fff;
      box-shadow: 0 2px 6px rgba(99, 102, 241, 0.35);
    }

    ::ng-deep .view-toggle .mat-button-toggle-checked mat-icon {
      color: #fff;
    }

    ::ng-deep .view-toggle .mat-button-toggle:not(.mat-button-toggle-checked) .mat-button-toggle-button:hover {
      background: rgba(99, 102, 241, 0.1);
      color: #4f46e5;
    }

    ::ng-deep .view-toggle mat-icon {
      font-size: 18px;
      width: 18px;
      height: 18px;
    }

    /* Neutralise la bordure interne par défaut de Material entre les toggles */
    ::ng-deep .view-toggle .mat-button-toggle + .mat-button-toggle {
      border-left: none !important;
    }

    ::ng-deep .view-toggle .mat-button-toggle-appearance-standard .mat-button-toggle-label-content {
      line-height: normal;
      padding: 0;
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
