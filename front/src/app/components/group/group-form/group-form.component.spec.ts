import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupFormComponent } from './group-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('GroupFormComponent', () => {
  let component: GroupFormComponent;
  let fixture: ComponentFixture<GroupFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(GroupFormComponent);

    fixture = TestBed.createComponent(GroupFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('refuse un formulaire vide : un groupe a besoin de son type, niveau et matière', () => {
    expect(component.groupForm.valid).toBeFalse();
    expect(component.groupForm.get('basicInformation.name')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.groupTypeId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.levelId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('basicInformation.subjectId')?.hasError('required')).toBeTrue();
  });

  it('exige aussi le nombre de séances par série, le tarif et l\'enseignant', () => {
    // Le nombre de séances par série et le tarif fondent le coût du mois : ils sont
    // obligatoires, sans quoi aucun montant ne peut être calculé.
    expect(component.groupForm.get('additionalDetails.sessionNumberPerSerie')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('additionalDetails.priceId')?.hasError('required')).toBeTrue();
    expect(component.groupForm.get('additionalDetails.teacherId')?.hasError('required')).toBeTrue();
    // La description reste libre.
    expect(component.groupForm.get('additionalDetails.description')?.valid).toBeTrue();
  });

  it('devient valide une fois toutes les références renseignées', () => {
    component.groupForm.get('basicInformation')?.setValue({
      name: 'Maths 1B', groupTypeId: 1, levelId: 2, subjectId: 3
    });
    component.groupForm.get('additionalDetails')?.setValue({
      sessionNumberPerSerie: 8, priceId: 4, description: '', teacherId: 5
    });

    expect(component.groupForm.valid).toBeTrue();
  });
});
