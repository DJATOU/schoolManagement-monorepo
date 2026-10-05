import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { EncashmentEditDialogComponent } from './encashment-edit-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../../testing/setup';
import { aGroup, anEncashment, aStudent } from '../../../../../testing/fixtures';
import { GroupService } from '../../../../services/group.service';
import { SeriesService } from '../../../../services/series.service';
import { SessionSeries } from '../../../../models/sessionSerie/sessionSerie';

/**
 * Saisie de l'état voulu d'un versement à corriger (spec admin-corrections, exigence 3.1).
 */
describe('EncashmentEditDialogComponent', () => {
  let fixture: ComponentFixture<EncashmentEditDialogComponent>;
  let component: EncashmentEditDialogComponent;
  let dialogRef: DialogRefSpy;
  let groups: jasmine.SpyObj<GroupService>;
  let series: jasmine.SpyObj<SeriesService>;

  const aSeries = (id: number, name: string, groupId = 5): SessionSeries =>
    ({ id, name, groupId, totalSessions: 2, sessionsCompleted: 0, numberOfSessionsCreated: 2 } as SessionSeries);

  /** Groupe 5 « Maths 1B » : Octobre (7), Novembre (8) ; Amina (42) et Lina (43). Groupe 6 « Physique ». */
  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    groups = jasmine.createSpyObj<GroupService>('GroupService', ['getGroupsForPayment', 'getStudentsByGroupId']);
    series = jasmine.createSpyObj<SeriesService>('SeriesService', ['getSeriesByGroupId']);
    groups.getGroupsForPayment.and.returnValue(of([aGroup(), aGroup({ id: 6, name: 'Physique' })]));
    groups.getStudentsByGroupId.and.callFake(groupId => of(groupId === 5
      ? [aStudent({ id: 42, firstName: 'Amina', lastName: 'Belkacem' }), aStudent({ id: 43, firstName: 'Lina', lastName: 'Haddad' })]
      : [aStudent({ id: 44, firstName: 'Sami', lastName: 'Kaci' })]));
    series.getSeriesByGroupId.and.callFake(groupId => of(groupId === 5
      ? [aSeries(7, 'Octobre'), aSeries(8, 'Novembre')]
      : [aSeries(9, 'Physique Octobre', 6)]));

    await setupComponentTestBed(EncashmentEditDialogComponent, {
      providers: [
        ...matDialogProviders({ encashment: anEncashment({ paymentMethod: 'cash', notes: null }) }, dialogRef),
        { provide: GroupService, useValue: groups },
        { provide: SeriesService, useValue: series }
      ]
    });
    fixture = TestBed.createComponent(EncashmentEditDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('part des valeurs du versement : rien n\'est encore changé', () => {
    expect(component.form.value).toEqual({
      amount: 5000, groupId: 5, studentId: 42, targetSeriesId: 7, paymentMethod: 'cash', notes: ''
    });
    expect(component.unchanged).toBeTrue();
    expect((fixture.nativeElement as HTMLElement).querySelector<HTMLButtonElement>('.ee-submit')!.disabled).toBeTrue();
  });

  it('propose les groupes où l\'élève peut payer, les élèves et les Séries du groupe choisi', () => {
    expect(groups.getGroupsForPayment).toHaveBeenCalledWith(42);
    expect(component.groups.map(g => g.label)).toEqual(['Maths 1B', 'Physique']);
    expect(component.students.map(s => s.label)).toEqual(['Amina Belkacem', 'Lina Haddad']);
    expect(component.series.map(s => s.label)).toEqual(['Octobre', 'Novembre']);
  });

  it('rend l\'état voulu complet, note et mode normalisés', () => {
    component.form.patchValue({ amount: '2000', targetSeriesId: 8, notes: '  Versé par le père  ' });

    component.submit();

    expect(dialogRef.close).toHaveBeenCalledWith({
      amount: 2000, studentId: 42, groupId: 5, targetSeriesId: 8, paymentMethod: 'cash', notes: 'Versé par le père'
    });
  });

  it('un autre groupe recharge ses Séries et ses élèves, et désélectionne ce qui n\'en fait pas partie', () => {
    component.form.get('groupId')!.setValue(6);

    expect(series.getSeriesByGroupId).toHaveBeenCalledWith(6);
    expect(component.series.map(s => s.label)).toEqual(['Physique Octobre']);
    expect(component.students.map(s => s.label)).toEqual(['Sami Kaci']);
    expect(component.form.get('targetSeriesId')!.value).toBeNull();
    expect(component.form.get('studentId')!.value).toBeNull();
    expect(component.form.invalid).toBeTrue();
  });

  it('le versement actuel reste choisissable, même absent des listes servies', () => {
    groups.getGroupsForPayment.and.returnValue(of([aGroup({ id: 6, name: 'Physique' })]));
    groups.getStudentsByGroupId.and.returnValue(of([]));
    series.getSeriesByGroupId.and.returnValue(of([]));

    component.ngOnInit();

    expect(component.groups.map(g => g.id)).toEqual([5, 6]);
    expect(component.students.map(s => s.id)).toEqual([42]);
    expect(component.series.map(s => s.id)).toEqual([7]);
    expect(component.form.get('targetSeriesId')!.value).toBe(7);
  });

  it('un montant nul ou absent bloque la suite', () => {
    component.form.patchValue({ amount: 0 });
    component.submit();
    component.form.patchValue({ amount: null });
    component.submit();

    expect(dialogRef.close).not.toHaveBeenCalled();
  });

  it('repart d\'une saisie précédente au retour de l\'Aperçu', async () => {
    TestBed.resetTestingModule();
    await setupComponentTestBed(EncashmentEditDialogComponent, {
      providers: [
        ...matDialogProviders({
          encashment: anEncashment(),
          changes: { amount: 2000, studentId: 43, groupId: 5, targetSeriesId: 8, paymentMethod: null, notes: 'Lina' }
        }, dialogRef),
        { provide: GroupService, useValue: groups },
        { provide: SeriesService, useValue: series }
      ]
    });
    const again = TestBed.createComponent(EncashmentEditDialogComponent);
    again.detectChanges();

    expect(again.componentInstance.form.value).toEqual({
      amount: 2000, groupId: 5, studentId: 43, targetSeriesId: 8, paymentMethod: null, notes: 'Lina'
    });
  });

  it('« Annuler » ferme sans rien rendre', () => {
    component.cancel();

    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
