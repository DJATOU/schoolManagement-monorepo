import { HttpClient } from '@angular/common/http';
import {
  Directive, ElementRef, Input, OnChanges, OnDestroy, Renderer2, SimpleChanges
} from '@angular/core';
import { Subscription } from 'rxjs';

/**
 * Charge l'image d'un `<img>` via `HttpClient` afin que le jeton d'authentification soit
 * transmis, puis affiche le résultat sous forme d'URL d'objet.
 *
 * <p><strong>Pourquoi :</strong> les photos sont servies par des points d'accès protégés
 * (`/personne/**` et `/api/students/{id}/photo`). Une balise `<img src="…">` déclenche une
 * requête du navigateur qui ne passe <em>pas</em> par l'intercepteur HTTP d'Angular : elle
 * part donc sans en-tête `Authorization` et se solde par un 401 depuis l'activation de la
 * sécurité JWT. En passant par `HttpClient`, l'intercepteur ajoute le `Bearer` et l'image
 * s'affiche, tout en restant protégée côté serveur (les photos sont des données
 * personnelles : les rendre publiques n'est pas souhaitable).</p>
 *
 * <p>Les sources locales (`data:` issues d'un aperçu de fichier, `blob:`) sont transmises
 * telles quelles, sans requête réseau.</p>
 *
 * <p>En cas d'échec, un évènement `error` est émis sur l'élément afin que les gestionnaires
 * existants (`(error)="onImageError()"`) basculent sur l'affichage des initiales.</p>
 *
 * Exemple : {@code <img [appSecureImage]="studentPhotoUrl" (error)="onImageError()">}
 */
@Directive({
  selector: 'img[appSecureImage]',
  standalone: true
})
export class SecureImageDirective implements OnChanges, OnDestroy {

  /** URL de la photo à charger (protégée ou locale). */
  @Input('appSecureImage') appSecureImage: string | null | undefined;

  private objectUrl: string | null = null;
  private sub?: Subscription;

  constructor(
    private el: ElementRef<HTMLImageElement>,
    private renderer: Renderer2,
    private http: HttpClient
  ) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['appSecureImage']) {
      this.load();
    }
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.releaseObjectUrl();
  }

  private load(): void {
    this.sub?.unsubscribe();
    this.releaseObjectUrl();

    const source = this.appSecureImage;
    if (!source) {
      this.renderer.removeAttribute(this.el.nativeElement, 'src');
      return;
    }

    // Aperçu local (FileReader) ou blob déjà résolu : aucune requête nécessaire.
    if (source.startsWith('data:') || source.startsWith('blob:')) {
      this.renderer.setAttribute(this.el.nativeElement, 'src', source);
      return;
    }

    this.sub = this.http.get(source, { responseType: 'blob' }).subscribe({
      next: (blob) => {
        this.objectUrl = URL.createObjectURL(blob);
        this.renderer.setAttribute(this.el.nativeElement, 'src', this.objectUrl);
      },
      error: () => {
        // Laisse les gestionnaires (error) du template basculer sur les initiales.
        this.renderer.removeAttribute(this.el.nativeElement, 'src');
        this.el.nativeElement.dispatchEvent(new Event('error'));
      }
    });
  }

  /** Libère l'URL d'objet précédente pour éviter une fuite mémoire. */
  private releaseObjectUrl(): void {
    if (this.objectUrl) {
      URL.revokeObjectURL(this.objectUrl);
      this.objectUrl = null;
    }
  }
}
