# Exemples d'utilisation de l'indicateur de statut de paiement

Ce fichier contient des exemples pratiques d'utilisation de l'indicateur de statut de paiement dans différents scénarios.

## 📦 Composants disponibles

### 1. StudentCardComponent (Cards)
Utilise automatiquement l'indicateur via ProfileCardComponent.

### 2. StudentListItemComponent (Liste/Table)
Affiche l'indicateur inline avec le nom de l'étudiant.

## 🚀 Exemple 1: Liste simple (chargement individuel)

```typescript
// Composant parent qui affiche une liste d'étudiants
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StudentService } from '@services/student.service';
import { StudentListItemComponent } from '@components/student/student-list/student-list-item/student-list-item.component';
import { Student } from '@models/student';

@Component({
  selector: 'app-students-page',
  standalone: true,
  imports: [CommonModule, StudentListItemComponent],
  template: `
    <div class="students-container">
      <h1>Liste des étudiants</h1>

      <!-- Chaque student-list-item charge son propre statut -->
      <app-student-list-item
        *ngFor="let student of students"
        [student]="student">
      </app-student-list-item>
    </div>
  `
})
export class StudentsPageComponent implements OnInit {
  students: Student[] = [];

  constructor(private studentService: StudentService) {}

  ngOnInit() {
    // Charger les étudiants
    this.studentService.getStudents().subscribe(students => {
      this.students = students;
      // Chaque StudentListItemComponent charge son propre statut automatiquement
    });
  }
}
```

**Avantages:**
- ✅ Simple à implémenter
- ✅ Pas de code supplémentaire

**Inconvénients:**
- ❌ Performance: N requêtes API (1 par étudiant)
- ❌ Temps de chargement plus long pour de grandes listes

## 🚀 Exemple 2: Liste optimisée (chargement parallèle)

```typescript
// Composant parent avec chargement optimisé des statuts
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StudentService } from '@services/student.service';
import { StudentPaymentStatusService } from '@services/student-payment-status.service';
import { StudentListItemComponent } from '@components/student/student-list/student-list-item/student-list-item.component';
import { Student } from '@models/student';
import { StudentPaymentStatus } from '@models/student-payment-status';

@Component({
  selector: 'app-students-page-optimized',
  standalone: true,
  imports: [CommonModule, StudentListItemComponent],
  template: `
    <div class="students-container">
      <h1>Liste des étudiants</h1>

      <div *ngIf="loading" class="loading">
        <mat-spinner></mat-spinner>
        <p>Chargement des statuts de paiement...</p>
      </div>

      <!-- Passer le statut pré-chargé à chaque item -->
      <app-student-list-item
        *ngFor="let student of students"
        [student]="student"
        [paymentStatus]="paymentStatusMap.get(student.id)">
      </app-student-list-item>
    </div>
  `,
  styles: [`
    .loading {
      display: flex;
      flex-direction: column;
      align-items: center;
      padding: 2rem;
      gap: 1rem;
    }
  `]
})
export class StudentsPageOptimizedComponent implements OnInit {
  students: Student[] = [];
  paymentStatusMap = new Map<number, StudentPaymentStatus>();
  loading = false;

  constructor(
    private studentService: StudentService,
    private paymentStatusService: StudentPaymentStatusService
  ) {}

  ngOnInit() {
    this.loadStudentsWithPaymentStatus();
  }

  /**
   * Charge les étudiants et leurs statuts de paiement en parallèle
   */
  private loadStudentsWithPaymentStatus(): void {
    this.loading = true;

    // 1. Charger les étudiants
    this.studentService.getStudents().subscribe(students => {
      this.students = students;

      // 2. Extraire les IDs
      const studentIds = students.map(s => s.id);

      // 3. Charger tous les statuts en parallèle (1 seul appel forkJoin)
      this.paymentStatusService.getMultipleStudentsPaymentStatus(studentIds).subscribe({
        next: (statusMap) => {
          this.paymentStatusMap = statusMap;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading payment statuses:', error);
          this.loading = false;
          // Continuer sans les statuts
        }
      });
    });
  }
}
```

**Avantages:**
- ✅ Performance: N requêtes en parallèle avec `forkJoin`
- ✅ Temps de chargement optimisé
- ✅ Meilleure UX (indicateur de chargement)

**Inconvénients:**
- ⚠️ Plus de code à écrire
- ⚠️ Nécessite de gérer l'état

## 🚀 Exemple 3: Pagination avec chargement par page

```typescript
// Composant avec pagination - charge les statuts uniquement pour la page courante
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { StudentService } from '@services/student.service';
import { StudentPaymentStatusService } from '@services/student-payment-status.service';
import { StudentCardComponent } from '@components/student/student-card/student-card.component';
import { Student } from '@models/student';
import { StudentPaymentStatus } from '@models/student-payment-status';

@Component({
  selector: 'app-students-paginated',
  standalone: true,
  imports: [CommonModule, MatPaginatorModule, StudentCardComponent],
  template: `
    <div class="students-container">
      <h1>Étudiants ({{ totalStudents }})</h1>

      <div class="students-grid">
        <app-student-card
          *ngFor="let student of currentPageStudents"
          [student]="student">
        </app-student-card>
      </div>

      <mat-paginator
        [length]="totalStudents"
        [pageSize]="pageSize"
        [pageSizeOptions]="[10, 20, 50]"
        (page)="onPageChange($event)">
      </mat-paginator>
    </div>
  `,
  styles: [`
    .students-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
      margin: 2rem 0;
    }
  `]
})
export class StudentsPaginatedComponent implements OnInit {
  allStudents: Student[] = [];
  currentPageStudents: Student[] = [];
  totalStudents = 0;
  pageSize = 10;
  pageIndex = 0;

  constructor(
    private studentService: StudentService,
    private paymentStatusService: StudentPaymentStatusService
  ) {}

  ngOnInit() {
    this.loadStudents();
  }

  private loadStudents(): void {
    this.studentService.getStudents().subscribe(students => {
      this.allStudents = students;
      this.totalStudents = students.length;
      this.loadCurrentPage();
    });
  }

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadCurrentPage();
  }

  /**
   * Charge uniquement les étudiants de la page courante
   * Les StudentCardComponent chargeront automatiquement leurs propres statuts
   */
  private loadCurrentPage(): void {
    const start = this.pageIndex * this.pageSize;
    const end = start + this.pageSize;
    this.currentPageStudents = this.allStudents.slice(start, end);
  }
}
```

**Avantages:**
- ✅ Performance: Seulement 10-20 requêtes par page
- ✅ Scalabilité: Fonctionne avec des milliers d'étudiants
- ✅ Chargement automatique via StudentCardComponent

## 🚀 Exemple 4: Filtre par statut de paiement

```typescript
// Composant avec filtre par statut
import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { StudentService } from '@services/student.service';
import { StudentPaymentStatusService } from '@services/student-payment-status.service';
import { StudentCardComponent } from '@components/student/student-card/student-card.component';
import { Student } from '@models/student';
import { StudentPaymentStatus } from '@models/student-payment-status';
import { forkJoin } from 'rxjs';

type PaymentFilter = 'ALL' | 'GOOD' | 'LATE';

@Component({
  selector: 'app-students-filtered',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonToggleModule, StudentCardComponent],
  template: `
    <div class="students-container">
      <div class="header">
        <h1>Étudiants</h1>

        <!-- Filtre par statut -->
        <mat-button-toggle-group [(value)]="filter" (change)="applyFilter()">
          <mat-button-toggle value="ALL">
            Tous ({{ stats.total }})
          </mat-button-toggle>
          <mat-button-toggle value="GOOD">
            À jour ({{ stats.good }})
          </mat-button-toggle>
          <mat-button-toggle value="LATE">
            En retard ({{ stats.late }})
          </mat-button-toggle>
        </mat-button-toggle-group>
      </div>

      <div class="students-grid">
        <app-student-card
          *ngFor="let student of filteredStudents"
          [student]="student">
        </app-student-card>
      </div>

      <p *ngIf="filteredStudents.length === 0" class="no-results">
        Aucun étudiant trouvé pour ce filtre.
      </p>
    </div>
  `,
  styles: [`
    .header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 2rem;
    }
    .students-grid {
      display: grid;
      grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
      gap: 1rem;
    }
    .no-results {
      text-align: center;
      color: #666;
      padding: 2rem;
    }
  `]
})
export class StudentsFilteredComponent implements OnInit {
  allStudents: Student[] = [];
  filteredStudents: Student[] = [];
  paymentStatusMap = new Map<number, StudentPaymentStatus>();

  filter: PaymentFilter = 'ALL';

  stats = {
    total: 0,
    good: 0,
    late: 0
  };

  constructor(
    private studentService: StudentService,
    private paymentStatusService: StudentPaymentStatusService
  ) {}

  ngOnInit() {
    this.loadStudentsWithStatus();
  }

  private loadStudentsWithStatus(): void {
    this.studentService.getStudents().subscribe(students => {
      this.allStudents = students;
      const studentIds = students.map(s => s.id);

      this.paymentStatusService.getMultipleStudentsPaymentStatus(studentIds).subscribe({
        next: (statusMap) => {
          this.paymentStatusMap = statusMap;
          this.calculateStats();
          this.applyFilter();
        }
      });
    });
  }

  private calculateStats(): void {
    this.stats.total = this.allStudents.length;
    this.stats.good = 0;
    this.stats.late = 0;

    this.allStudents.forEach(student => {
      const status = this.paymentStatusMap.get(student.id);
      if (status?.paymentStatus === 'GOOD') {
        this.stats.good++;
      } else if (status?.paymentStatus === 'LATE') {
        this.stats.late++;
      }
    });
  }

  applyFilter(): void {
    if (this.filter === 'ALL') {
      this.filteredStudents = this.allStudents;
    } else {
      this.filteredStudents = this.allStudents.filter(student => {
        const status = this.paymentStatusMap.get(student.id);
        return status?.paymentStatus === this.filter;
      });
    }
  }
}
```

**Avantages:**
- ✅ UX: Filtrage instantané côté client
- ✅ Statistiques en temps réel
- ✅ Pas de rechargement à chaque filtre

## 🎯 Recommandations par scénario

| Scénario | Nb d'étudiants | Méthode recommandée |
|----------|----------------|---------------------|
| Liste simple | < 20 | Exemple 1 (chargement individuel) |
| Liste moyenne | 20-100 | Exemple 2 (chargement parallèle) |
| Grande liste | > 100 | Exemple 3 (pagination) |
| Filtres/Stats | Tout | Exemple 4 (filtre par statut) |

## 🔧 Configuration avancée

### Cache des statuts (éviter de recharger)

```typescript
import { Injectable } from '@angular/core';
import { Observable, of, tap } from 'rxjs';
import { StudentPaymentStatus } from '@models/student-payment-status';
import { StudentPaymentStatusService } from './student-payment-status.service';

@Injectable({
  providedIn: 'root'
})
export class CachedPaymentStatusService {
  private cache = new Map<number, { status: StudentPaymentStatus; timestamp: number }>();
  private cacheDuration = 2 * 60 * 1000; // 2 minutes

  constructor(private paymentStatusService: StudentPaymentStatusService) {}

  getStudentPaymentStatus(studentId: number): Observable<StudentPaymentStatus> {
    const cached = this.cache.get(studentId);

    // Si en cache et pas expiré
    if (cached && Date.now() - cached.timestamp < this.cacheDuration) {
      return of(cached.status);
    }

    // Sinon charger et mettre en cache
    return this.paymentStatusService.getStudentPaymentStatus(studentId).pipe(
      tap(status => {
        this.cache.set(studentId, {
          status,
          timestamp: Date.now()
        });
      })
    );
  }

  clearCache(): void {
    this.cache.clear();
  }
}
```

### Lazy Loading avec Intersection Observer

```typescript
// Charger le statut uniquement quand la card devient visible
import { Component, Input, OnInit, ElementRef } from '@angular/core';
import { StudentPaymentStatusService } from '@services/student-payment-status.service';

@Component({
  selector: 'app-student-card-lazy',
  template: `...`
})
export class StudentCardLazyComponent implements OnInit {
  @Input() student!: Student;
  paymentStatus?: StudentPaymentStatus;

  constructor(
    private el: ElementRef,
    private paymentStatusService: StudentPaymentStatusService
  ) {}

  ngOnInit() {
    // Créer un observer
    const observer = new IntersectionObserver((entries) => {
      entries.forEach(entry => {
        // Charger uniquement quand visible
        if (entry.isIntersecting && !this.paymentStatus) {
          this.loadPaymentStatus();
          observer.unobserve(entry.target);
        }
      });
    });

    observer.observe(this.el.nativeElement);
  }

  private loadPaymentStatus(): void {
    this.paymentStatusService.getStudentPaymentStatus(this.student.id).subscribe(
      status => this.paymentStatus = status
    );
  }
}
```

---

**Auteur**: Claude Code
**Date**: 2025-12-17
**Version**: 1.0.0
