import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditSessionDialogComponent } from './edit-session-dialogue.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';
import { aSession } from '../../../../../testing/fixtures';

/**
 * Édition d'une séance existante.
 *
 * <p>Le formulaire est découpé en sous-groupes (`sessionDetails`, `sessionTiming`) : la spec
 * vérifie qu'ils existent et qu'ils sont préremplis, un sous-groupe manquant faisant échouer
 * l'enregistrement de façon silencieuse.</p>
 */
describe('EditSessionDialogComponent', () => {
  let component: EditSessionDialogComponent;
  let fixture: ComponentFixture<EditSessionDialogComponent>;
  let dialogRef: DialogRefSpy;

  const session = aSession();

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EditSessionDialogComponent, {
      providers: matDialogProviders({ session }, dialogRef)
    });
    fixture = TestBed.createComponent(EditSessionDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte la séance à éditer', () => {
    expect(component.data.session).toEqual(session);
  });

  it('construit les deux sous-groupes du formulaire', () => {
    expect(component.sessionForm.get('sessionDetails')).toBeTruthy();
    expect(component.sessionForm.get('sessionTiming')).toBeTruthy();
  });

  it('préremplit le formulaire avec le titre actuel de la séance', () => {
    expect(JSON.stringify(component.sessionForm.value)).toContain('Séance 1');
  });
});
