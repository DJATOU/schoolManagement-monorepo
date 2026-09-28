import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupDialogComponent } from './group-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';

/**
 * Dialogue d'affectation d'un étudiant à des groupes.
 *
 * <p>Le constructeur lit `data.allGroups` : sans cette donnée, il échoue avant même que le
 * formulaire soit construit. Le point sous test est que la sélection est
 * <strong>obligatoire</strong> — fermer sur une liste vide affecterait l'étudiant à rien tout
 * en laissant croire à une affectation.</p>
 */
describe('GroupDialogComponent', () => {
  let component: GroupDialogComponent;
  let fixture: ComponentFixture<GroupDialogComponent>;
  let dialogRef: DialogRefSpy;

  const allGroups = [aGroup(), aGroup({ id: 6, name: 'Physique 1B' })];

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(GroupDialogComponent, {
      providers: matDialogProviders({ allGroups }, dialogRef)
    });
    fixture = TestBed.createComponent(GroupDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('propose les groupes qu\'on lui confie', () => {
    expect(component.allGroups).toEqual(allGroups);
  });

  it('exige au moins un groupe : le formulaire est invalide à vide', () => {
    expect(component.groupForm.valid).toBeFalse();
  });

  it('ne ferme rien tant qu\'aucun groupe n\'est choisi', () => {
    component.onSubmit();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('ferme sur les identifiants choisis', () => {
    component.groupForm.get('groupIds')!.setValue([5, 6]);

    component.onSubmit();

    expect(dialogRef.close).toHaveBeenCalledWith([5, 6]);
  });

  it('ferme sans valeur à l\'annulation', () => {
    // Fermer sur une liste vide serait interprété comme une affectation à aucun groupe ;
    // l'absence de valeur dit « annulé ».
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
