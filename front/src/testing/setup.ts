import { EnvironmentProviders, Provider, Type } from '@angular/core';
import { TestBed, TestModuleMetadata } from '@angular/core/testing';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatNativeDateModule } from '@angular/material/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { of } from 'rxjs';

/**
 * Harnais de test partagé du frontend.
 *
 * Point de définition unique de la configuration `TestBed` commune à toutes les specs :
 * recopier ces imports dans chaque fichier garantirait qu'ils divergent.
 *
 * Contenu du socle commun :
 *  - `HttpClientTestingModule` : les services métier injectent tous `HttpClient`.
 *  - `TranslateModule.forRoot()` : fournit `TranslateService` et le pipe `translate`.
 *  - `NoopAnimationsModule` : neutralise les animations Material (pas de timers en attente).
 *  - `MatSnackBarModule` : `MatSnackBar` est `providedIn: MatSnackBarModule`, plusieurs
 *    composants l'injectent sans l'importer eux-mêmes.
 *  - `MatNativeDateModule` : `DateAdapter` pour les datepickers Material.
 *  - `provideRouter([])` : fournit `Router` et `ActivatedRoute` (route racine).
 *
 * @see setupComponentTestBed pour un composant standalone
 * @see setupServiceTestBed pour un service
 */

/** Imports communs à toute spec (composant ou service). */
export function commonTestingImports(): TestModuleMetadata['imports'] {
  return [
    HttpClientTestingModule,
    TranslateModule.forRoot(),
    NoopAnimationsModule,
    MatSnackBarModule,
    MatNativeDateModule,
  ];
}

/** Providers communs à toute spec. */
export function commonTestingProviders(): (Provider | EnvironmentProviders)[] {
  return [provideRouter([])];
}

/**
 * Compose les métadonnées d'un test avec le socle commun.
 *
 * Les imports et providers passés par la spec sont ajoutés après ceux du socle,
 * ce qui leur permet de surcharger un provider commun si besoin.
 */
export function withCommonTesting(metadata: TestModuleMetadata = {}): TestModuleMetadata {
  const { imports = [], providers = [], ...rest } = metadata;
  return {
    ...rest,
    imports: [...(commonTestingImports() ?? []), ...imports],
    providers: [...commonTestingProviders(), ...providers],
  };
}

/**
 * Configure et compile le `TestBed` pour un composant standalone.
 *
 * @param component Le composant standalone sous test.
 * @param metadata Imports / providers supplémentaires propres à la spec
 *        (par exemple `MatDialogRef` ou `MAT_DIALOG_DATA` pour un dialogue).
 */
export async function setupComponentTestBed<T>(
  component: Type<T>,
  metadata: TestModuleMetadata = {}
): Promise<void> {
  const { imports = [], ...rest } = metadata;
  await TestBed.configureTestingModule(
    withCommonTesting({ ...rest, imports: [component, ...imports] })
  ).compileComponents();
}

/**
 * Configure le `TestBed` pour un service.
 *
 * @param metadata Imports / providers supplémentaires propres à la spec.
 */
export function setupServiceTestBed(metadata: TestModuleMetadata = {}): void {
  TestBed.configureTestingModule(withCommonTesting(metadata));
}

/**
 * Espion de `MatDialogRef` : permet de vérifier qu'un dialogue se ferme, et avec quoi.
 *
 * Un dialogue dont on ne teste pas la valeur de fermeture ne teste pas l'essentiel : c'est
 * elle que l'appelant interprète comme « confirmé » ou « annulé ».
 */
export type DialogRefSpy = jasmine.SpyObj<{ close: (result?: unknown) => void }>;

/** Crée l'espion de `MatDialogRef` attendu par {@link matDialogProviders}. */
export function createDialogRefSpy(): DialogRefSpy {
  return jasmine.createSpyObj<{ close: (result?: unknown) => void }>('MatDialogRef', ['close']);
}

/**
 * Providers d'un dialogue Material : la référence de dialogue et ses données d'entrée.
 *
 * Les specs générées par le CLI omettaient les deux, alors qu'un dialogue les injecte dans
 * son constructeur : elles échouaient sur `No provider for MatDialogRef!` sans rien dire du
 * composant.
 *
 * @param data Données injectées via `MAT_DIALOG_DATA`.
 * @param dialogRef Espion de fermeture ; créé à la demande si omis.
 */
export function matDialogProviders(data: unknown, dialogRef: DialogRefSpy = createDialogRefSpy()): Provider[] {
  return [
    { provide: MatDialogRef, useValue: dialogRef },
    { provide: MAT_DIALOG_DATA, useValue: data }
  ];
}

/**
 * Remplace `ActivatedRoute` par une route porteuse des paramètres donnés.
 *
 * Les écrans de profil lisent `route.snapshot.paramMap.get('id')`. Sous `provideRouter([])`,
 * ce paramètre est absent : le composant retombe sur son chemin « aucun identifiant » et ne
 * charge rien, si bien qu'une spec ne testerait que la branche dégénérée.
 *
 * @param params Paramètres de route, valeurs sous forme de chaîne comme dans une vraie URL.
 */
export function activatedRouteProviders(params: Record<string, string>): Provider[] {
  return [{
    provide: ActivatedRoute,
    useValue: {
      snapshot: {
        paramMap: convertToParamMap(params),
        queryParamMap: convertToParamMap({})
      },
      paramMap: of(convertToParamMap(params)),
      queryParams: of({}),
      params: of(params)
    }
  }];
}
