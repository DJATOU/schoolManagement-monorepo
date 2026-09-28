import { Directive, Input, OnDestroy, OnInit, TemplateRef, ViewContainerRef } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { Role } from '../models/auth/auth.model';

/**
 * Directive structurelle `*appHasRole` : n'affiche l'élément que si l'utilisateur courant
 * possède le rôle requis. Sert à masquer les commandes d'écriture pour un VIEWER.
 *
 * <p>Rappel : ce masquage est cosmétique ; le backend reste l'autorité (défense en profondeur).</p>
 *
 * Exemple : {@code <button *appHasRole="'ADMIN'"> Supprimer </button>}
 */
@Directive({
  selector: '[appHasRole]',
  standalone: true
})
export class HasRoleDirective implements OnInit, OnDestroy {
  private requiredRole: Role = 'ADMIN';
  private hasView = false;
  private sub?: Subscription;

  @Input('appHasRole') set appHasRole(role: Role) {
    this.requiredRole = role;
    this.update();
  }

  constructor(
    private templateRef: TemplateRef<unknown>,
    private viewContainer: ViewContainerRef,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.sub = this.authService.currentUser$.subscribe(() => this.update());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private update(): void {
    const allowed = this.authService.hasRole(this.requiredRole);
    if (allowed && !this.hasView) {
      this.viewContainer.createEmbeddedView(this.templateRef);
      this.hasView = true;
    } else if (!allowed && this.hasView) {
      this.viewContainer.clear();
      this.hasView = false;
    }
  }
}
