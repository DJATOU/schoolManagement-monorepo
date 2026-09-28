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
import { SecureImageDirective } from '../../../../shared/secure-image.directive';
import { TranslateService } from '@ngx-translate/core';
import { buildLatePaymentTooltip } from '../../../../utils/payment-status-tooltip';

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
  ,
    SecureImageDirective
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
  hasImageError: boolean = false;
  avatarColor: string = '#6366f1';

  // Palette d'avatars (cohérente avec profile-card)
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private router: Router,
    private paymentStatusService: StudentPaymentStatusService,
    private translate: TranslateService
  ) {}

  ngOnInit(): void {
    this.setAvatarColor();

    // Générer l'URL complète de la photo seulement si une photo existe.
    // Sinon on retombe sur les initiales colorées (pas d'image cassée).
    if (this.student?.photo) {
      this.studentPhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.student.photo}`;
    }

    // Charger le statut de paiement si non fourni par le parent
    if (!this.paymentStatus && this.student?.id) {
      this.loadPaymentStatus();
    }
  }

  /**
   * Initiales de l'étudiant (max 2 caractères) pour l'avatar par défaut.
   */
  getInitials(): string {
    const firstInitial = (this.student?.firstName || '').charAt(0).toUpperCase();
    const lastInitial = (this.student?.lastName || '').charAt(0).toUpperCase();
    return (firstInitial + lastInitial) || 'XX';
  }

  /**
   * Couleur d'avatar déterministe basée sur le nom.
   */
  private setAvatarColor(): void {
    const name = `${this.student?.firstName || ''}${this.student?.lastName || ''}`;
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Fallback initiales si l'image ne charge pas.
   */
  onImageError(): void {
    this.hasImageError = true;
  }

  navigateToStudent(student: Student) {
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
      case 'EXEMPT':
        return 'volunteer_activism';
      case 'NA':
        return 'remove_circle_outline';
      default:
        return '';
    }
  }

  /**
   * Retourne le label traduit du statut de paiement.
   */
  getPaymentLabel(): string {
    const status = this.paymentStatus?.paymentStatus;
    return status ? this.translate.instant(`PAYMENT_STATUS.${status}`) : '';
  }

  /** Détail des retards de paiement, mis en forme par l'utilitaire partagé. */
  getPaymentTooltip(): string {
    return buildLatePaymentTooltip(this.paymentStatus, this.translate);
  }
}
