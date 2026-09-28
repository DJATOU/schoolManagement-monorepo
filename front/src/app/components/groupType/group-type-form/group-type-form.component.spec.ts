import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupTypeFormComponent } from './group-type-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('GroupTypeFormComponent', () => {
  let component: GroupTypeFormComponent;
  let fixture: ComponentFixture<GroupTypeFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupTypeFormComponent);

    fixture = TestBed.createComponent(GroupTypeFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en mode création (aucun identifiant dans l\'URL)', () => {
    expect(component.editingId).toBeNull();
  });

  it('exige un nom et une taille d\'au moins un étudiant', () => {
    expect(component.groupTypeForm.valid).toBeFalse();
    expect(component.groupTypeForm.get('groupTypeDetails.name')?.hasError('required')).toBeTrue();

    component.groupTypeForm.get('groupTypeDetails')?.setValue({ name: 'Petit groupe', size: 0 });
    // Un type de groupe de taille nulle n'a pas de sens : aucun effectif ne peut y tenir.
    expect(component.groupTypeForm.get('groupTypeDetails.size')?.hasError('min')).toBeTrue();

    component.groupTypeForm.get('groupTypeDetails.size')?.setValue('8');
    expect(component.groupTypeForm.valid).toBeTrue();
  });
});
