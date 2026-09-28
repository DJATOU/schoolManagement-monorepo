import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCard, MatCardActions, MatCardContent, MatCardHeader, MatCardSubtitle, MatCardTitle } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { MatListModule } from '@angular/material/list';
import { MatButtonModule } from '@angular/material/button';
import { MatTooltipModule } from '@angular/material/tooltip';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { environment } from '../../../../environments/environment';  // Import des variables d'environnement

@Component({
  selector: 'app-profile-list-item',
  standalone: true,
  imports: [CommonModule, MatListModule, MatIcon, MatCard, MatCardHeader, MatCardTitle, MatCardContent, MatCardActions, MatCardSubtitle, MatButtonModule, MatTooltipModule,
    SecureImageDirective
  ],
  templateUrl: './profile-list-item.component.html',
  styleUrls: ['./profile-list-item.component.scss']
})
export class ProfileListItemComponent {
  @Input() profile: any;
  @Input() profileType: 'student' | 'teacher' = 'student';  // Peut être étendu à d'autres types de profils

  profilePhotoUrl: string = '';  // Variable pour stocker l'URL complète de la photo
  hasImageError: boolean = false;
  avatarColor: string = '#6366f1';

  // Palette d'avatars (cohérente avec profile-card / student-list-item)
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  ngOnInit() {
    this.setAvatarColor();

    // Générer l'URL de la photo seulement si elle existe.
    // Sinon on affiche les initiales colorées (pas d'image cassée).
    if (this.profile?.photo) {
      this.profilePhotoUrl = `${environment.apiUrl}${environment.imagesPath}${this.profile.photo}`;
    }
  }

  /**
   * Initiales du profil (max 2 caractères) pour l'avatar par défaut.
   */
  getInitials(): string {
    const firstInitial = (this.profile?.firstName || '').charAt(0).toUpperCase();
    const lastInitial = (this.profile?.lastName || '').charAt(0).toUpperCase();
    return (firstInitial + lastInitial) || 'XX';
  }

  /**
   * Couleur d'avatar déterministe basée sur le nom.
   */
  private setAvatarColor(): void {
    const name = `${this.profile?.firstName || ''}${this.profile?.lastName || ''}`;
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Fallback initiales si l'image ne charge pas.
   */
  onImageError(): void {
    this.hasImageError = true;
  }

  /**
   * Ouvre Gmail avec l'email pré-rempli
   */
  openEmail(event: Event): void {
    event.stopPropagation(); // Empêche la navigation vers le profil
    if (this.profile?.email) {
      window.open(`mailto:${this.profile.email}`, '_blank');
    }
  }

  /**
   * Ouvre WhatsApp avec le numéro de téléphone
   */
  openWhatsApp(event: Event): void {
    event.stopPropagation(); // Empêche la navigation vers le profil
    if (this.profile?.phoneNumber) {
      // Nettoyer le numéro de téléphone (enlever espaces, tirets, etc.)
      const cleanPhone = this.profile.phoneNumber.replace(/[\s\-\(\)]/g, '');
      // Ajouter le code pays si nécessaire (exemple: +33 pour France)
      const phoneNumber = cleanPhone.startsWith('+') ? cleanPhone : `+212${cleanPhone}`;
      window.open(`https://wa.me/${phoneNumber}`, '_blank');
    }
  }
}
