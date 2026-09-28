import { environment } from '../environments/environment';

/**
 * URL de base de l'API backend.
 *
 * Isolé de {@code app.config.ts} (qui importe {@code app.routes} et, par
 * transitivité, l'ensemble des composants) afin d'éviter une dépendance
 * circulaire au chargement des modules : les services importent uniquement
 * cette constante, sans tirer le graphe des routes/composants.
 */
export const API_BASE_URL = environment.apiUrl;
