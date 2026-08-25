export const environment = {
  production: true,
  /**
   * Volontairement vide : en production, le front et l'API sont servis par le
   * même nginx, sur une seule origine. Les services construisent donc des URLs
   * relatives (`/api/...`), que nginx relaie vers le service Spring.
   *
   * Conséquences : aucune configuration CORS n'est nécessaire, un seul port est
   * exposé, et le build reste valable quelle que soit l'adresse IP ou le nom de
   * la machine qui l'héberge — rien n'est à recompiler si le réseau change.
   */
  apiUrl: '',
  imagesPath: '/personne/'
};
