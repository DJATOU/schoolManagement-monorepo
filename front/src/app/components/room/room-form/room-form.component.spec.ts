import { ComponentFixture, TestBed } from '@angular/core/testing';

import { RoomFormComponent } from './room-form.component';
import { setupComponentTestBed } from '../../../../testing/setup';

describe('RoomFormComponent', () => {
  let component: RoomFormComponent;
  let fixture: ComponentFixture<RoomFormComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(RoomFormComponent);

    fixture = TestBed.createComponent(RoomFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en mode création (aucun identifiant dans l\'URL)', () => {
    expect(component.editingId).toBeNull();
  });

  it('refuse un formulaire vide et exige nom et capacité', () => {
    expect(component.roomForm.valid).toBeFalse();
    expect(component.roomForm.get('roomDetails.name')?.hasError('required')).toBeTrue();
    expect(component.roomForm.get('roomDetails.capacity')?.hasError('required')).toBeTrue();
  });

  it('accepte un formulaire complet et rejette une capacité négative', () => {
    component.roomForm.get('roomDetails')?.setValue({
      name: 'Salle A',
      capacity: '24',
      description: ''
    });
    expect(component.roomForm.valid).toBeTrue();

    component.roomForm.get('roomDetails.capacity')?.setValue(-1);
    expect(component.roomForm.get('roomDetails.capacity')?.hasError('min')).toBeTrue();
  });
});
