import { Directive, ElementRef, OnDestroy, OnInit, Renderer2 } from '@angular/core';
import { Subscription } from 'rxjs';
import { AuthService } from '../services/auth.service';

/**
 * Directive d'attribut `appAdminOnly` : **désactive et grise** l'élément hôte (bouton, lien)
 * lorsque l'utilisateur courant n'est pas ADMIN. Sert à rendre les commandes d'écriture
 * (Enregistrer, Modifier, Supprimer, Ajouter, Inscription…) inactives pour un VIEWER.
 *
 * <p>Rappel : le backend reste l'autorité (403 sur écriture pour un VIEWER) ; ce grisage est
 * une commodité d'interface (défense en profondeur).</p>
 *
 * Exemple : {@code <button mat-raised-button appAdminOnly>Enregistrer</button>}
 */
@Directive({
  selector: '[appAdminOnly]',
  standalone: true
})
export class AdminOnlyDirective implements OnInit, OnDestroy {
  private sub?: Subscription;

  constructor(
    private el: ElementRef<HTMLElement>,
    private renderer: Renderer2,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.sub = this.authService.currentUser$.subscribe(() => this.apply());
    this.apply();
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private apply(): void {
    const node = this.el.nativeElement;
    const isAdmin = this.authService.hasRole('ADMIN');

    if (isAdmin) {
      this.renderer.removeAttribute(node, 'disabled');
      (node as HTMLButtonElement).disabled = false;
      this.renderer.removeClass(node, 'viewer-disabled');
      this.renderer.removeAttribute(node, 'title');
    } else {
      this.renderer.setAttribute(node, 'disabled', 'true');
      (node as HTMLButtonElement).disabled = true;
      this.renderer.addClass(node, 'viewer-disabled');
      this.renderer.setAttribute(node, 'title', 'Action réservée aux administrateurs');
    }
  }
}
