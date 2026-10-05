import { ComponentFixture, TestBed } from '@angular/core/testing';

import { GroupDialogComponent } from './group-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aGroup } from '../../../../testing/fixtures';
import { calendarDayOf } from '../../../utils/calendar-day';

/**
 * Dialogue d'affectation d'un étudiant à des groupes.
 *
 * <p>Le constructeur lit `data.allGroups` : sans cette donnée, il échoue avant même que le
 * formulaire soit construit. Le point sous test est que la sélection est
 * <strong>obligatoire</strong> — fermer sur une liste vide affecterait l'étudiant à rien tout
 * en laissant croire à une affectation — et que la date d'arrivée réelle part avec elle (C.8,
 * exigence 5.1).</p>
 */
describe('GroupDialogComponent', () => {
  let component: GroupDialogComponent;
  let fixture: ComponentFixture<GroupDialogComponent>;
  let dialogRef: DialogRefSpy;

  const allGroups = [aGroup(), aGroup({ id: 6, name: 'Physique 1B' })];

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(GroupDialogComponent, {
      providers: matDialogProviders({ allGroups, yearStart: '2029-09-01', yearEnd: '2030-06-30' }, dialogRef)
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

    component.groupForm.get('groupIds')!.setValue([]);
    component.onSubmit();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('propose l\'arrivée au jour même et ferme sur les groupes et la date choisis', () => {
    expect(component.groupForm.get('arrival')!.value).toBe(calendarDayOf());

    component.groupForm.get('groupIds')!.setValue([5, 6]);
    component.groupForm.get('arrival')!.setValue('2029-10-15');
    component.onSubmit();

    expect(dialogRef.close).toHaveBeenCalledWith({ groupIds: [5, 6], arrival: '2029-10-15' });
  });

  it('une date vide ou inexistante bloque l\'inscription', () => {
    component.groupForm.get('groupIds')!.setValue([5]);
    component.groupForm.get('arrival')!.setValue('2030-02-30');
    component.onSubmit();
    component.groupForm.get('arrival')!.setValue('');
    component.onSubmit();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('borne le calendrier à l\'année scolaire affichée', () => {
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type="date"]')!;

    expect(input.getAttribute('min')).toBe('2029-09-01');
    expect(input.getAttribute('max')).toBe('2030-06-30');
  });

  it('ferme sans valeur à l\'annulation', () => {
    // Fermer sur une liste vide serait interprété comme une affectation à aucun groupe ;
    // l'absence de valeur dit « annulé ».
    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
