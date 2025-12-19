import { Component, Input, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { Router } from '@angular/router';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { environment } from '../../../../environments/environment';
import { StudentPaymentStatus } from '../../../models/student-payment-status';

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
  imports: [MatCardModule, CommonModule, MatButtonModule, MatTooltipModule, MatIconModule, MatChipsModule],
  templateUrl: './profile-card.component.html',
  styleUrls: ['./profile-card.component.scss']
})
export class ProfileCardComponent implements OnInit {
  @Input() profile!: Profile;
  @Input() profileType!: string;
  @Input() paymentStatus?: StudentPaymentStatus; // Statut de paiement (optionnel, uniquement pour les étudiants)
  profilePhotoUrl: string = '';
  isFlipped: boolean = false;

  constructor(private router: Router) {}

  ngOnInit(): void {
    console.log('Profile data:', this.profile);
    if (!this.profile) {
      console.error('Profile is null or undefined:', this.profile);
    } else if (!this.profile.id) {
      console.error('Profile ID is missing:', this.profile);
    } else {
      console.log('Profile is properly defined:', this.profile);

      // Générer l'URL complète de la photo de profil
      if (this.profile.photo) {
        this.profilePhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.profile.photo}`;
      } else {
        this.profilePhotoUrl = 'assets/default-avatar.png';  // Utiliser une image par défaut si aucune photo n'est disponible
      }
    }
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
   * Affiche la liste des groupes avec le nombre de sessions impayées et les montants
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
