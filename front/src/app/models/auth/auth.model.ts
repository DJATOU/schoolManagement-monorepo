/** Rôles applicatifs (miroir de l'enum backend). */
export type Role = 'ADMIN' | 'VIEWER';

/** Identité de l'utilisateur courant, exposée à l'UI. */
export interface AuthUser {
  username: string;
  role: Role;
}

/** Requête de connexion. */
export interface LoginRequest {
  username: string;
  password: string;
}

/** Réponse de connexion renvoyée par le backend. */
export interface AuthResponse {
  token: string;
  username: string;
  role: Role;
  expiresAt: string; // ISO Instant
}

/** Représentation d'un compte utilisateur (gestion des comptes, sans mot de passe). */
export interface UserAccount {
  id: number;
  username: string;
  role: Role;
  enabled: boolean;
}
