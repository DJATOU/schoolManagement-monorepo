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

@Component({
  selector: 'app-group-card',
  standalone: true,
  imports: [MatCardModule, CommonModule, MatButtonModule, MatIconModule, MatTooltipModule],
  templateUrl: './group-card.component.html',
  styleUrls: ['./group-card.component.scss']
})
export class GroupCardComponent implements OnInit {
  @Input() group!: Group;
  @Input() levels: Level[] = [];
  @Input() groupTypes: GroupType[] = [];

  level: string = 'Unknown Level';
  type: string = 'Unknown Type';
  groupPhotoUrl: string = '';
  isFlipped: boolean = false;
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
    this.setLevelAndType();
    this.setPhotoUrl();
    this.setAvatarColor();
  }

  private setLevelAndType(): void {
    const levelData = this.levels.find(level => level.id === this.group.levelId);
    const typeData = this.groupTypes.find(type => type.id === this.group.groupTypeId);

    // Assurez-vous que `levelData` et `typeData` existent avant d'accéder à leurs propriétés
    this.level = levelData?.name || 'Unknown Level';
    this.type = typeData?.name || 'Unknown Type';
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
