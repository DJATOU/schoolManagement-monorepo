import { Component, Input, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatMenuModule } from '@angular/material/menu';
import { environment } from '../../../../environments/environment';
import { StudentPaymentStatus } from '../../../models/student-payment-status';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Tutor } from '../../../models/tutor/tutor';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { buildLatePaymentTooltip } from '../../../utils/payment-status-tooltip';

interface Profile {
  id: string;
  firstName: string;
  lastName: string;
  photo: string;
  subtitle?: string;
  email?: string;
  phoneNumber?: string;
  level?: number;
}

@Component({
  selector: 'app-profile-card',
  standalone: true,
  imports: [MatCardModule, CommonModule, MatButtonModule, MatTooltipModule, MatIconModule, MatChipsModule, MatMenuModule, TranslateModule,
    SecureImageDirective
  ],
  templateUrl: './profile-card.component.html',
  styleUrls: ['./profile-card.component.scss']
})
export class ProfileCardComponent implements OnInit {
  @Input() profile!: Profile;
  @Input() profileType!: string;
  @Input() paymentStatus?: StudentPaymentStatus; // Statut de paiement (optionnel, uniquement pour les étudiants)
  @Input() tutor?: Tutor | null; // Tuteur (optionnel, uniquement pour les étudiants)
  profilePhotoUrl: string = '';
  isFlipped: boolean = false;
  hasImageError: boolean = false;
  avatarColor: string = '#6366f1';

  // Colors for avatar backgrounds
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(private router: Router, private translate: TranslateService) { }

  ngOnInit(): void {
    console.log('Profile data:', this.profile);
    if (!this.profile) {
      console.error('Profile is null or undefined:', this.profile);
    } else if (!this.profile.id) {
      console.error('Profile ID is missing:', this.profile);
    } else {
      console.log('Profile is properly defined:', this.profile);
      this.setAvatarColor();

      // Générer l'URL complète de la photo de profil
      if (this.profile.photo) {
        this.profilePhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.profile.photo}`;
      }
    }
  }

  /**
   * Get initials from first and last name (max 2 characters)
   */
  getInitials(): string {
    const firstName = this.profile?.firstName || '';
    const lastName = this.profile?.lastName || '';
    const firstInitial = firstName.charAt(0).toUpperCase();
    const lastInitial = lastName.charAt(0).toUpperCase();
    return firstInitial + lastInitial || 'XX';
  }

  /**
   * Set avatar color based on profile name
   */
  private setAvatarColor(): void {
    const name = `${this.profile?.firstName || ''}${this.profile?.lastName || ''}`;
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  navigateToProfile(): void {
    if (this.profile && this.profile.id) {
      this.router.navigate([`/${this.profileType}`, this.profile.id]);
    } else {
      console.error('Profile ID is null or undefined');
    }
  }

  /**
   * Ouvre Gmail avec l'email pré-rempli
   */
  sendEmail(event: Event): void {
    event.stopPropagation();
    if (this.profile?.email) {
      window.open(`mailto:${this.profile.email}`, '_blank');
    }
  }

  /**
   * Ouvre WhatsApp avec le numéro de téléphone
   */
  callPhone(event: Event): void {
    event.stopPropagation();
    if (this.profile?.phoneNumber) {
      // Nettoyer le numéro de téléphone (enlever espaces, tirets, etc.)
      const cleanPhone = this.profile.phoneNumber.replace(/[\s\-\(\)]/g, '');
      // Ajouter le code pays si nécessaire (exemple: +212 pour Maroc)
      const phoneNumber = cleanPhone.startsWith('+') ? cleanPhone : `+212${cleanPhone}`;
      window.open(`https://wa.me/${phoneNumber}`, '_blank');
    }
  }

  /**
   * Bascule l'état de flip de la card
   */
  toggleFlip(event: Event): void {
    event.stopPropagation();
    this.isFlipped = !this.isFlipped;
  }

  /**
   * Ouvre WhatsApp avec le numéro du tuteur.
   */
  openTutorWhatsApp(event: Event): void {
    event.stopPropagation();
    if (this.tutor?.phoneNumber) {
      const cleanPhone = this.tutor.phoneNumber.replace(/[\s\-\(\)]/g, '');
      const phoneNumber = cleanPhone.startsWith('+') ? cleanPhone : `+212${cleanPhone}`;
      window.open(`https://wa.me/${phoneNumber}`, '_blank');
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

  /**
   * Handle image load error - fallback to initials
   */
  onImageError(event: Event): void {
    this.hasImageError = true;
  }
}

