import { CommonModule } from '@angular/common';
import { Component, inject } from '@angular/core';
import { RouterLink, RouterModule, RouterOutlet } from '@angular/router';
import { NavigationComponent } from "./components/navigation/navigation.component";
import { SideMenuComponent } from './components/side-menu/side-menu.component';
import { StudentSearchComponent } from "./components/student/student-search/student-search.component";
import { MatSidenav, MatSidenavContainer, MatSidenavContent } from '@angular/material/sidenav';
import { SharedLayoutContainerComponent } from './components/shared/shared-layout-container/shared-layout-container.component';
import { AuthService } from './services/auth.service';
import { TranslationService } from './services/translation.service';

@Component({
    selector: 'app-root',
    standalone: true,
    templateUrl: './app.component.html',
    styleUrl: './app.component.scss',
    imports: [CommonModule, RouterOutlet, RouterLink, NavigationComponent, SideMenuComponent, StudentSearchComponent, RouterModule,
MatSidenav,MatSidenavContainer,MatSidenavContent,SharedLayoutContainerComponent]
})
export class AppComponent {
  title = 'schoolManagement-front';

  isSidenavOpen = true;

  /**
   * Instancié ici pour appliquer la langue enregistrée (textes, `lang`, `dir`) dès la page de
   * connexion : la barre de navigation, qui était seule à l'injecter, n'y est pas affichée.
   */
  protected readonly translation = inject(TranslationService);

  /** Exposé au template pour afficher le shell uniquement quand l'utilisateur est connecté. */
  constructor(public auth: AuthService) {}

  toggleSidenav() {
    this.isSidenavOpen = !this.isSidenavOpen;
  }
}
