import { Direction, Directionality } from '@angular/cdk/bidi';
import { DOCUMENT } from '@angular/common';
import { Injectable, inject } from '@angular/core';

/**
 * Sens d'écriture de l'application, modifiable à chaud.
 *
 * <p>Le `Directionality` du CDK lit `dir` une seule fois, à sa création : passer en arabe sans
 * recharger la page laisserait menus, dialogues et sidenav en gauche-à-droite. Fourni à la place
 * du `Directionality` racine (voir `app.config.ts`), ce service garde une valeur mutable : les
 * composants créés ensuite (overlays, dialogues, menus) lisent la nouvelle valeur, ceux déjà
 * affichés réagissent à `change`.</p>
 */
@Injectable({ providedIn: 'root' })
export class AppDirectionality extends Directionality {
  override value: Direction;

  constructor() {
    const doc = inject(DOCUMENT, { optional: true });
    super(doc ?? undefined);
    this.value = doc?.documentElement?.dir === 'rtl' ? 'rtl' : 'ltr';
  }

  /** Change le sens d'écriture ; `change` n'émet que si la valeur change réellement. */
  setDirection(dir: Direction): void {
    if (dir === this.value) {
      return;
    }
    this.value = dir;
    this.change.emit(dir);
  }
}
