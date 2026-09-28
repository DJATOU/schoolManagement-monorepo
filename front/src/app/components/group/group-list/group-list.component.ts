import { Component, Input, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCard, MatCardContent } from '@angular/material/card';
import { MatIcon } from '@angular/material/icon';
import { Router } from '@angular/router';
import { Group } from '../../../models/group/group';
import { GroupService } from '../../../services/group.service';
import { SecureImageDirective } from '../../../shared/secure-image.directive';

@Component({
  selector: 'app-group-list',
  standalone: true,
  imports: [
    CommonModule,
    MatCard,
    MatCardContent,
    MatIcon
  ,
    SecureImageDirective
  ],
  templateUrl: './group-list.component.html',
  styleUrl: './group-list.component.scss'
})
export class GroupListComponent implements OnInit {
  @Input() group!: Group;
  @Input() levels: any[] = [];
  @Input() groupTypes: any[] = [];

  groupPhotoUrl: string = '';
  level: string = '—';
  type: string = '—';
  avatarColor: string = '#6366f1';

  // Colors for avatar backgrounds
  private avatarColors = [
    '#6366f1', '#8b5cf6', '#ec4899', '#ef4444', '#f97316',
    '#eab308', '#22c55e', '#14b8a6', '#06b6d4', '#3b82f6'
  ];

  constructor(
    private router: Router,
    private groupService: GroupService
  ) { }

  ngOnInit(): void {
    this.setPhotoUrl();
    this.setLevelAndType();
    this.setAvatarColor();
  }

  private setPhotoUrl(): void {
    if (this.group.photo && this.group.id) {
      this.groupPhotoUrl = this.groupService.getGroupPhotoUrl(this.group.id);
    }
  }

  /**
   * Résout le niveau et le type du groupe.
   *
   * <p>Les libellés fournis par le backend ({@code levelName} / {@code groupTypeName})
   * sont prioritaires ; les tableaux de référence ne servent que de repli pour les
   * écrans qui les chargent déjà.</p>
   */
  private setLevelAndType(): void {
    const levelFromLookup = this.levels.find(l => l.id === this.group.levelId)?.name;
    const typeFromLookup = this.groupTypes.find(t => t.id === this.group.groupTypeId)?.name;

    this.level = this.group.levelName || levelFromLookup || '—';
    this.type = this.group.groupTypeName || typeFromLookup || '—';
  }

  private setAvatarColor(): void {
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
    if (this.group.id) {
      this.router.navigate(['/group', this.group.id]);
    }
  }

  /**
   * Handle image error - fallback to initials
   */
  onImageError(): void {
    this.groupPhotoUrl = '';
  }
}
