import { HttpHandler, HttpRequest, HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { AuthInterceptor } from './auth.interceptor';

/**
 * Test de propriété (paramétré, ≥100 itérations) de l'intercepteur d'authentification.
 *
 * Feature: authentication-authorization, Property 12: L'intercepteur joint le justificatif aux requêtes protégées.
 *
 * Pour toute requête sortante vers une ressource protégée alors qu'un jeton est présent,
 * l'intercepteur ajoute l'en-tête `Authorization: Bearer <token>`. La requête de connexion
 * (`/api/v1/auth/login`) n'est jamais modifiée.
 *
 * fast-check n'étant pas disponible dans le projet, la propriété est vérifiée par une
 * génération pseudo-aléatoire exhaustive de 100+ cas (URL × méthode × jeton).
 */
describe('AuthInterceptor — Property 12 (Bearer sur requêtes protégées)', () => {

  const ITERATIONS = 120;
  const PROTECTED_PATHS = [
    '/api/students', '/api/teachers', '/api/groups', '/api/sessions',
    '/api/payments', '/api/v1/users', '/api/catch-ups', '/api/discounts'
  ];
  const METHODS = ['GET', 'POST', 'PUT', 'PATCH', 'DELETE'];

  // Générateur pseudo-aléatoire déterministe (reproductible).
  function makeRng(seed: number): () => number {
    let s = seed >>> 0;
    return () => {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 0xffffffff;
    };
  }

  function pick<T>(rng: () => number, arr: T[]): T {
    return arr[Math.floor(rng() * arr.length) % arr.length];
  }

  function randomToken(rng: () => number): string {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789.-_';
    let len = 10 + Math.floor(rng() * 30);
    let t = '';
    for (let i = 0; i < len; i++) {
      t += chars.charAt(Math.floor(rng() * chars.length));
    }
    return t;
  }

  interface CapturingHandler extends HttpHandler {
    lastRequest: HttpRequest<unknown> | null;
  }

  function makeHandler(): CapturingHandler {
    const handler = {
      lastRequest: null as HttpRequest<unknown> | null,
      handle(req: HttpRequest<unknown>) {
        handler.lastRequest = req;
        return of(new HttpResponse({ status: 200 }));
      }
    };
    return handler;
  }

  function buildRequest(method: string, url: string): HttpRequest<unknown> {
    switch (method) {
      case 'GET':
        return new HttpRequest('GET', url);
      case 'DELETE':
        return new HttpRequest('DELETE', url);
      case 'POST':
        return new HttpRequest('POST', url, {});
      case 'PUT':
        return new HttpRequest('PUT', url, {});
      default:
        return new HttpRequest('PATCH', url, {});
    }
  }

  it('ajoute Authorization: Bearer <token> sur toute requête protégée avec jeton présent', () => {
    const rng = makeRng(0x5eed);

    for (let i = 0; i < ITERATIONS; i++) {
      const token = randomToken(rng);
      const authService = { getToken: () => token, logout: () => {} } as any;
      const router = { navigate: () => {} } as any;
      const interceptor = new AuthInterceptor(authService, router);

      const method = pick(rng, METHODS);
      const path = pick(rng, PROTECTED_PATHS);
      const handler = makeHandler();

      interceptor.intercept(buildRequest(method, path), handler).subscribe();

      const outgoing = handler.lastRequest!;
      expect(outgoing.headers.get('Authorization'))
        .withContext(`itération ${i} — ${method} ${path}`)
        .toBe(`Bearer ${token}`);
    }
  });

  it('ne modifie jamais la requête de connexion (pas de jeton encore)', () => {
    const rng = makeRng(0x1234);

    for (let i = 0; i < ITERATIONS; i++) {
      const token = randomToken(rng);
      const authService = { getToken: () => token, logout: () => {} } as any;
      const router = { navigate: () => {} } as any;
      const interceptor = new AuthInterceptor(authService, router);

      const method = pick(rng, METHODS);
      const handler = makeHandler();

      interceptor.intercept(buildRequest(method, '/api/v1/auth/login'), handler).subscribe();

      expect(handler.lastRequest!.headers.get('Authorization'))
        .withContext(`login ne doit pas porter de Bearer — itération ${i}`)
        .toBeNull();
    }
  });
});
