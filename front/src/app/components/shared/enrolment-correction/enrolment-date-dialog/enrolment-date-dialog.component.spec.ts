import { TestBed } from '@angular/core/testing';

import { EnrolmentDateDialogComponent, EnrolmentDateDialogData } from './enrolment-date-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';
import { Enrolment } from '../../../../models/enrolment/enrolment';
import { calendarDayOf } from '../../../../utils/calendar-day';

/**
 * Saisie de la date d'une correction d'inscription (C.8 ; exigences 5.5, 6.1, 6.3) : départ proposé
 * au jour même, date déjà enregistrée refusée, jour rendu sans conversion.
 */
describe('EnrolmentDateDialogComponent', () => {
  let dialogRef: DialogRefSpy;

  const open: Enrolment = {
    id: 7, studentId: 1, groupId: 5, groupName: 'Math 1ère A', schoolYearId: 3,
    arrival: '2029-09-01', departure: null, active: true
  };
  const closed: Enrolment = { ...open, departure: '2030-01-14', active: false };

  async function create(data: EnrolmentDateDialogData): Promise<EnrolmentDateDialogComponent> {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(EnrolmentDateDialogComponent, { providers: matDialogProviders(data, dialogRef) });
    const fixture = TestBed.createComponent(EnrolmentDateDialogComponent);
    fixture.detectChanges();
    return fixture.componentInstance;
  }

  const setDate = (component: EnrolmentDateDialogComponent, value: string): void =>
    component.form.get('date')!.setValue(value);

  it('arrivée : proposée à sa date actuelle, qu\'il faut changer pour continuer', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: open, studentName: 'Amine Belkacem' });

    expect(component.form.get('date')!.value).toBe('2029-09-01');
    expect(component.unchanged).toBeTrue();
    component.submit();
    expect(dialogRef.close).not.toHaveBeenCalled();

    setDate(component, '2030-01-10');
    expect(component.unchanged).toBeFalse();
    component.submit();
    expect(dialogRef.close).toHaveBeenCalledWith({ kind: 'ARRIVAL', date: '2030-01-10', removePresencesAfter: false });
  });

  it('départ d\'une inscription ouverte : proposé au jour même (6.1)', async () => {
    const component = await create({ kind: 'DEPARTURE', enrolment: open, studentName: 'Amine Belkacem' });

    expect(component.form.get('date')!.value).toBe(calendarDayOf());
    expect(component.unchanged).toBeFalse();
    expect(component.titleKey).toBe('enrolment.correction.DEPARTURE.title');
  });

  it('départ : retrait des présences postérieures transmis s\'il est coché (6.3)', async () => {
    const component = await create({ kind: 'DEPARTURE', enrolment: open, studentName: 'Amine Belkacem' });
    setDate(component, '2030-01-14');
    component.form.get('removePresencesAfter')!.setValue(true);

    component.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ kind: 'DEPARTURE', date: '2030-01-14', removePresencesAfter: true });
  });

  it('arrivée : la case de retrait, même cochée, ne part pas', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: open, studentName: 'Amine Belkacem' });
    setDate(component, '2030-01-10');
    component.form.get('removePresencesAfter')!.setValue(true);

    component.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({ kind: 'ARRIVAL', date: '2030-01-10', removePresencesAfter: false });
  });

  it('départ déjà enregistré : « corriger », proposé à sa date, bornes du calendrier', async () => {
    const component = await create({ kind: 'DEPARTURE', enrolment: closed, studentName: 'Amine Belkacem' });

    expect(component.titleKey).toBe('enrolment.correction.DEPARTURE.correctTitle');
    expect(component.form.get('date')!.value).toBe('2030-01-14');
    expect(component.unchanged).toBeTrue();
    expect(component.min).toBe('2029-09-01');
    expect(component.max).toBeNull();
    expect(component.period).toEqual({ key: 'enrolment.period.closed',
      params: { arrival: '01/09/2029', departure: '14/01/2030' } });
  });

  it('arrivée d\'une inscription close : au plus tard le jour du départ', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: closed, studentName: 'Amine Belkacem' });

    expect(component.titleKey).toBe('enrolment.correction.ARRIVAL.title');
    expect(component.min).toBeNull();
    expect(component.max).toBe('2030-01-14');
  });

  it('inscription ouverte : période « à partir du »', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: open, studentName: 'Amine Belkacem' });

    expect(component.period).toEqual({ key: 'enrolment.period.open', params: { arrival: '01/09/2029' } });
  });

  it('retour de l\'Aperçu : la saisie précédente est reprise', async () => {
    const component = await create({ kind: 'DEPARTURE', enrolment: open, studentName: 'Amine Belkacem',
      previous: { kind: 'DEPARTURE', date: '2030-02-04', removePresencesAfter: true } });

    expect(component.form.get('date')!.value).toBe('2030-02-04');
    expect(component.form.get('removePresencesAfter')!.value).toBeTrue();
  });

  it('date vide ou inexistante : rien ne part', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: open, studentName: 'Amine Belkacem' });

    for (const invalid of ['', '2030-02-30']) {
      setDate(component, invalid);
      component.submit();
    }

    expect(component.form.invalid).toBeTrue();
    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('« Annuler » ferme sans valeur', async () => {
    const component = await create({ kind: 'ARRIVAL', enrolment: open, studentName: 'Amine Belkacem' });

    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
