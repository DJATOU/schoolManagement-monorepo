import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { AddStudentsDialogComponent } from './add-students-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';
import { StudentService } from '../../student/services/student.service';
import { calendarDayOf } from '../../../utils/calendar-day';

/**
 * Ajout d'élèves à un groupe depuis sa fiche (C.8 ; exigence 5.1) : la date d'arrivée réelle part
 * avec la sélection, proposée au jour même, bornée à l'année du groupe.
 */
describe('AddStudentsDialogComponent', () => {
  let fixture: ComponentFixture<AddStudentsDialogComponent>;
  let component: AddStudentsDialogComponent;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    const students = jasmine.createSpyObj<StudentService>('StudentService', ['getStudentsByLevel']);
    students.getStudentsByLevel.and.returnValue(of([
      aStudent({ id: 1, firstName: 'Amine', lastName: 'Belkacem' }),
      aStudent({ id: 2, firstName: 'Lina', lastName: 'Haddad' }),
      aStudent({ id: 3, firstName: 'Sami', lastName: 'Aït' })
    ]));
    await setupComponentTestBed(AddStudentsDialogComponent, {
      providers: [
        ...matDialogProviders({ levelId: 4, existingStudentIds: [3], yearStart: '2029-09-01', yearEnd: '2030-06-30' },
          dialogRef),
        { provide: StudentService, useValue: students }
      ]
    });
    fixture = TestBed.createComponent(AddStudentsDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('propose l\'arrivée au jour même, et ne liste pas les membres actuels', () => {
    expect(component.arrival).toBe(calendarDayOf());
    expect(component.students.map(student => student.id)).toEqual([1, 2]);
  });

  it('ferme sur les élèves choisis et leur date d\'arrivée commune', () => {
    component.toggle(2);
    component.toggle(1);
    component.arrival = '2029-11-04';

    component.onConfirm();

    expect(dialogRef.close).toHaveBeenCalledWith({ studentIds: [2, 1], arrival: '2029-11-04' });
  });

  it('sans élève choisi, ou avec une date inexistante : rien ne part', () => {
    component.onConfirm();
    component.toggle(1);
    component.arrival = '2030-02-30';
    component.onConfirm();
    component.arrival = '';
    component.onConfirm();

    expect(component.arrivalValid).toBeFalse();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('borne le calendrier à l\'année du groupe', async () => {
    await fixture.whenStable();
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>('input[type="date"]')!;

    expect(input.getAttribute('min')).toBe('2029-09-01');
    expect(input.getAttribute('max')).toBe('2030-06-30');
  });

  it('« Annuler » ferme sans sélection', () => {
    component.onCancel();

    expect(dialogRef.close).toHaveBeenCalledWith(null);
  });
});
