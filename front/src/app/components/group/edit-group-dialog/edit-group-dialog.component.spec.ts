import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EditGroupDialogComponent } from './edit-group-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';

/**
 * Édition d'un groupe existant.
 *
 * <p>`data.group` est déréférencé à la construction pour préremplir le formulaire. Le point
 * sous test est ce préremplissage : un formulaire d'édition ouvert vide ferait perdre les
 * valeurs actuelles dès l'enregistrement.</p>
 */
describe('EditGroupDialogComponent', () => {
  let component: EditGroupDialogComponent;
  let fixture: ComponentFixture<EditGroupDialogComponent>;
  let dialogRef: DialogRefSpy;

  const group = aGroup();

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EditGroupDialogComponent, {
      providers: matDialogProviders({ group }, dialogRef)
    });
    fixture = TestBed.createComponent(EditGroupDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte le groupe à éditer', () => {
    expect(component.data.group).toEqual(group);
  });

  it('préremplit le formulaire avec le nom actuel du groupe', () => {
    // Un champ vide à l'ouverture effacerait le nom existant au premier enregistrement.
    expect(JSON.stringify(component.editGroupForm.value)).toContain('Maths 1B');
  });
});
