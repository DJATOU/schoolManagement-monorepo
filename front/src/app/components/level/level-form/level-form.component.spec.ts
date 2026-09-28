import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LevelFormComponent } from './level-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('LevelFormComponent', () => {
  let component: LevelFormComponent;
  let fixture: ComponentFixture<LevelFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(LevelFormComponent);

    fixture = TestBed.createComponent(LevelFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en mode création (aucun identifiant dans l\'URL)', () => {
    expect(component.editingId).toBeNull();
  });

  it('refuse un formulaire vide et exige nom, code et rang', () => {
    expect(component.levelForm.valid).toBeFalse();
    expect(component.levelForm.get('name')?.hasError('required')).toBeTrue();
    expect(component.levelForm.get('levelCode')?.hasError('required')).toBeTrue();
    expect(component.levelForm.get('levelSequence')?.hasError('required')).toBeTrue();
    // La description reste optionnelle.
    expect(component.levelForm.get('description')?.valid).toBeTrue();
  });

  it('accepte un formulaire complet et rejette un rang inférieur à 1', () => {
    component.levelForm.setValue({
      name: 'Première année',
      levelCode: '1AS',
      levelSequence: 1,
      description: ''
    });
    expect(component.levelForm.valid).toBeTrue();

    component.levelForm.get('levelSequence')?.setValue(0);
    expect(component.levelForm.get('levelSequence')?.hasError('min')).toBeTrue();
  });
});
