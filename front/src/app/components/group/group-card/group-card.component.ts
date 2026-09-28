import { Component, Input, OnInit } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Group } from '../../../models/group/group';
import { Level } from '../../../models/level/level';
import { GroupType } from '../../../models/GroupType/groupType';
import { Router } from '@angular/router';
import { GroupService } from '../../../services/group.service';
import { SecureImageDirective } from '../../../shared/secure-image.directive';
import { TranslateModule } from '@ngx-translate/core';
import { SchoolYearContextService } from '../../../services/school-year-context.service';

@Component({
  selector: 'app-group-card',
  standalone: true,
  imports: [MatCardModule, CommonModule, MatButtonModule, MatIconModule, MatTooltipModule,
    SecureImageDirective, TranslateModule
  ],
  templateUrl: './group-card.component.html',
  styleUrls: ['./group-card.component.scss']
})
export class GroupCardComponent implements OnInit {
  @Input() group!: Group;
  @Input() levels: Level[] = [];
  @Input() groupTypes: GroupType[] = [];

  level: string = '—';
  type: string = '—';
  groupPhotoUrl: string = '';
  isFlipped: boolean = false;
  avatarColor: string = '#6366f1';

  /**
   * Vrai lorsque le groupe appartient à une autre année scolaire que celle sélectionnée.
   *
   * <p>La liste des groupes est filtrée sur l'année sélectionnée. Un groupe d'une année
   * antérieure restait donc visible sur la fiche étudiante tout en étant introuvable dans
   * la liste des groupes, sans aucune indication. On le signale désormais explicitement ;
   * la consultation reste possible, seule la modification est refusée.</p>
   */
  outsideSelectedYear = false;

  // Colors for avatar backgrounds
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private router: Router,
    private groupService: GroupService,
    private schoolYearContext: SchoolYearContextService
  ) { }

  ngOnInit(): void {
    this.setLevelAndType();
    this.setPhotoUrl();
    this.setAvatarColor();
    this.setSchoolYearFlag();
  }

  private setSchoolYearFlag(): void {
    const selected = this.schoolYearContext.getSelectedSchoolYear();
    this.outsideSelectedYear = selected?.id != null
      && this.group.schoolYearId != null
      && this.group.schoolYearId !== selected.id;
  }

  /**
   * Résout le niveau et le type du groupe.
   *
   * <p>On privilégie les libellés déjà fournis par le backend ({@code levelName} /
   * {@code groupTypeName}, renseignés par {@code GroupMapper}). Les tableaux
   * {@code levels} / {@code groupTypes} ne servent que de repli pour les appelants qui les
   * fournissent : s'appuyer uniquement sur eux affichait « Unknown Level » dès qu'un
   * écran ne les chargeait pas (cas du profil enseignant).</p>
   */
  private setLevelAndType(): void {
    const levelFromLookup = this.levels.find(level => level.id === this.group.levelId)?.name;
    const typeFromLookup = this.groupTypes.find(type => type.id === this.group.groupTypeId)?.name;

    this.level = this.group.levelName || levelFromLookup || '—';
    this.type = this.group.groupTypeName || typeFromLookup || '—';
  }

  private setPhotoUrl(): void {
    if (this.group.photo && this.group.id) {
      this.groupPhotoUrl = this.groupService.getGroupPhotoUrl(this.group.id);
    }
  }

  private setAvatarColor(): void {
    // Generate consistent color based on group name
    const name = this.group.name || '';
    const hash = name.split('').reduce((acc, char) => acc + char.charCodeAt(0), 0);
    this.avatarColor = this.avatarColors[hash % this.avatarColors.length];
  }

  /**
   * Get initials from group name (max 2 characters)
   */
  getInitials(): string {
    const name = this.group.name || '';
    const words = name.trim().split(/\s+/);
    if (words.length >= 2) {
      return (words[0][0] + words[1][0]).toUpperCase();
    }
    return name.substring(0, 2).toUpperCase();
  }

  navigateToGroupProfile(): void {
    this.router.navigate(['/group', this.group.id]);
  }

  toggleFlip(event: Event): void {
    event.stopPropagation();
    this.isFlipped = !this.isFlipped;
  }

  /**
   * Handle image error - fallback to initials
   */
  onImageError(): void {
    this.groupPhotoUrl = ''; // Clear URL to show initials
  }
}
