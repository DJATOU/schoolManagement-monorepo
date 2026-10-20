import { formatDate, formatNumber } from '@angular/common';
import { registerAppLocales, resolveLocale } from './locale';

describe('resolveLocale', () => {
  it('ramène chaque langue de l\'interface à une locale enregistrée', () => {
    expect(resolveLocale('fr')).toBe('fr');
    expect(resolveLocale('en')).toBe('en');
    expect(resolveLocale('ar')).toBe('ar-DZ');
  });

  it('ignore la région et la casse', () => {
    expect(resolveLocale('en-GB')).toBe('en');
    expect(resolveLocale('FR-fr')).toBe('fr');
    expect(resolveLocale('ar-SA')).toBe('ar-DZ');
  });

  it('ramène une langue absente ou inconnue au français', () => {
    expect(resolveLocale(undefined)).toBe('fr');
    expect(resolveLocale(null)).toBe('fr');
    expect(resolveLocale('')).toBe('fr');
    expect(resolveLocale('de')).toBe('fr');
  });
});

describe('registerAppLocales', () => {
  beforeAll(() => registerAppLocales());

  it('permet de formater en arabe (ar-DZ) sans NG0701, avec des chiffres latins', () => {
    const locale = resolveLocale('ar');
    let date = '';
    let amount = '';
    expect(() => {
      date = formatDate(new Date(2026, 0, 15), 'longDate', locale);
      amount = formatNumber(1234.5, locale, '1.0-2');
    }).not.toThrow();

    // Mois algérien (« جانفي » pour janvier) et chiffres latins, jamais arabes-indiens (٠-٩).
    expect(date).toContain('جانفي');
    expect(date).toContain('15');
    expect(date).toContain('2026');
    expect(date).not.toMatch(/[\u0660-\u0669]/);
    expect(amount).toMatch(/1.?234,5/);
    expect(amount).not.toMatch(/[\u0660-\u0669]/);
  });

  it('garde le français et l\'anglais disponibles', () => {
    expect(formatDate(new Date(2026, 4, 15), 'longDate', resolveLocale('fr'))).toBe('15 mai 2026');
    expect(formatDate(new Date(2026, 4, 15), 'longDate', resolveLocale('en'))).toBe('May 15, 2026');
  });
});
