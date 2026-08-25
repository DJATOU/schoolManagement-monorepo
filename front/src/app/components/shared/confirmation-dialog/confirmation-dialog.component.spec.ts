import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ConfirmationDialogComponent } from './confirmation-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Dialogue de confirmation générique.
 *
 * <p>Ce qui compte ici est la <strong>valeur de fermeture</strong> : c'est elle que
 * l'appelant interprète comme un accord. Un dialogue qui renverrait une valeur vraie à
 * l'annulation déclencherait la suppression qu'on venait de refuser.</p>
 */
describe('ConfirmationDialogComponent', () => {
  let component: ConfirmationDialogComponent;
  let fixture: ComponentFixture<ConfirmationDialogComponent>;
  let dialogRef: DialogRefSpy;

  const data = { title: 'Supprimer l\'étudiant', message: 'Cette action est définitive.' };

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(ConfirmationDialogComponent, {
      providers: matDialogProviders(data, dialogRef)
    });
    fixture = TestBed.createComponent(ConfirmationDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le message qu\'on lui confie', () => {
    expect(fixture.nativeElement.textContent).toContain('Cette action est définitive.');
  });

  it('expose les données reçues', () => {
    expect(component.data).toEqual(data);
  });

  it('ferme sur « vrai » à la confirmation', () => {
    component.onConfirm();

    expect(dialogRef.close).toHaveBeenCalledWith(true);
  });

  it('ferme sur « faux » à l\'annulation', () => {
    // Une annulation qui renverrait une valeur vraie déclencherait l'action qu'on refuse.
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
