import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { Role } from '../models/auth/auth.model';

/**
 * Garde d'authentification : bloque l'accès aux vues métier sans connexion valide
 * (redirection vers /login).
 */
export const authGuard: CanActivateFn = () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};

/**
 * Garde de rôle : réserve certaines vues à un rôle donné (ex. ADMIN). Un utilisateur connecté
 * mais sans le rôle requis est renvoyé vers le tableau de bord.
 */
export const roleGuard = (role: Role): CanActivateFn => () => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (!authService.isAuthenticated()) {
    router.navigate(['/login']);
    return false;
  }
  if (authService.hasRole(role)) {
    return true;
  }
  router.navigate(['/dashboard']);
  return false;
};
