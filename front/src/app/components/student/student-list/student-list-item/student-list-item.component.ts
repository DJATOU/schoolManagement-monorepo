import { Component, Input, Output, EventEmitter, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common'; // Importer CommonModule
import { Student } from '../../domain/student';
import { MatListItem } from '@angular/material/list';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatChipsModule } from '@angular/material/chips';
import { Router } from '@angular/router';
import { environment } from '../../../../../environments/environment';
import { MatIcon } from '@angular/material/icon';
import { StudentPaymentStatusService } from '../../../../services/student-payment-status.service';
import { StudentPaymentStatus } from '../../../../models/student-payment-status';

@Component({
  selector: 'app-student-list-item',
  standalone: true,
  imports: [
    CommonModule, // Ajouter CommonModule ici
    MatListItem,
    MatCard,
    MatCardContent,
    MatIcon,
    MatButtonModule,
    MatTooltipModule,
    MatChipsModule
  ],
  templateUrl: './student-list-item.component.html',
  styleUrls: ['./student-list-item.component.scss']
})
export class StudentListItemComponent implements OnInit {
  @Input() student!: Student;  // Accepte un objet étudiant
  @Input() showDeleteButton: boolean = false; // Contrôle du bouton "Supprimer"
  @Input() paymentStatus?: StudentPaymentStatus; // Statut de paiement (optionnel, peut être passé par le parent)
  @Output() deleteStudent = new EventEmitter<Student>(); // Événement pour notifier la suppression

  studentPhotoUrl: string = '';

  constructor(
    private router: Router,
    private paymentStatusService: StudentPaymentStatusService
  ) {}

  ngOnInit(): void {
    // Générer dynamiquement l'URL complète de la photo de l'étudiant
    if (this.student?.photo) {
      this.studentPhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.student.photo}`;
    } else {
      this.studentPhotoUrl = 'assets/default-avatar.png';  // Utiliser un avatar par défaut si aucune photo
    }

    // Charger le statut de paiement si non fourni par le parent
    if (!this.paymentStatus && this.student?.id) {
      this.loadPaymentStatus();
    }
  }

  navigateToStudent(student: Student) {
    console.log("rrrrrrrrrrrr");
    this.router.navigate(['/student', student.id]); // En supposant que /student/:id est votre route
  }

  onDeleteStudent(event: Event): void {
    event.stopPropagation(); // Empêche le clic de se propager au parent
    this.deleteStudent.emit(this.student);
  }

  /**
   * Ouvre Gmail avec l'email pré-rempli
   */
  openEmail(event: Event): void {
    event.stopPropagation(); // Empêche la navigation vers le profil
    if (this.student?.email) {
      window.open(`mailto:${this.student.email}`, '_blank');
    }
  }

  /**
   * Ouvre WhatsApp avec le numéro de téléphone
   */
  openWhatsApp(event: Event): void {
    event.stopPropagation(); // Empêche la navigation vers le profil
    if (this.student?.phoneNumber) {
      // Nettoyer le numéro de téléphone (enlever espaces, tirets, etc.)
      const cleanPhone = this.student.phoneNumber.replace(/[\s\-\(\)]/g, '');
      // Ajouter le code pays si nécessaire (exemple: +212 pour Maroc)
      const phoneNumber = cleanPhone.startsWith('+') ? cleanPhone : `+212${cleanPhone}`;
      window.open(`https://wa.me/${phoneNumber}`, '_blank');
    }
  }

  /**
   * Charge le statut de paiement de l'étudiant
   * @private
   */
  private loadPaymentStatus(): void {
    if (this.student && this.student.id) {
      this.paymentStatusService.getStudentPaymentStatus(this.student.id).subscribe({
        next: (status) => {
          this.paymentStatus = status;
        },
        error: (error) => {
          console.error('Error loading payment status:', error);
          this.paymentStatus = undefined;
        }
      });
    }
  }

  /**
   * Retourne l'icône appropriée selon le statut de paiement
   */
  getPaymentIcon(): string {
    if (!this.paymentStatus) return '';

    switch (this.paymentStatus.paymentStatus) {
      case 'GOOD':
        return 'check_circle';
      case 'LATE':
        return 'warning';
      case 'NA':
        return 'remove_circle_outline';
      default:
        return '';
    }
  }

  /**
   * Retourne le label approprié selon le statut de paiement
   */
  getPaymentLabel(): string {
    if (!this.paymentStatus) return '';

    switch (this.paymentStatus.paymentStatus) {
      case 'GOOD':
        return 'À jour';
      case 'LATE':
        return 'En retard';
      case 'NA':
        return 'N/A';
      default:
        return '';
    }
  }

  /**
   * Génère le texte du tooltip pour les retards de paiement
   */
  getPaymentTooltip(): string {
    if (!this.paymentStatus || this.paymentStatus.paymentStatus !== 'LATE') {
      return '';
    }

    const lines: string[] = ['Paiements en retard:'];

    for (const lateGroup of this.paymentStatus.lateGroups) {
      const remaining = lateGroup.dueAmount - lateGroup.paidAmount;
      lines.push(
        `• ${lateGroup.groupName}: ${lateGroup.unpaidSessionsCount} session(s) - ` +
        `Reste ${remaining.toFixed(2)} DA (${lateGroup.paidAmount.toFixed(2)}/${lateGroup.dueAmount.toFixed(2)} DA)`
      );
    }

    return lines.join('\n');
  }
}
