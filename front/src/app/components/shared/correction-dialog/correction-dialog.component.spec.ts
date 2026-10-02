import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { CorrectionDialogComponent, CorrectionDialogData } from './correction-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { CorrectionPreview, CorrectionReason, CorrectionResponse } from '../../../models/correction/correction';
import { CorrectionError } from '../../../models/correction/correction-error';
import { CorrectionStep } from '../../../services/encashment.service';

/**
 * Le dialogue commun des corrections (spec admin-corrections, B.7 ; exigences 4 et 11.1-11.2) :
 * Motif choisi, Aperçu, confirmation de cet Aperçu, reprise sur un Aperçu périmé.
 */
describe('CorrectionDialogComponent', () => {
  let fixture: ComponentFixture<CorrectionDialogComponent<string>>;
  let component: CorrectionDialogComponent<string>;
  let dialogRef: DialogRefSpy;
  let run: jasmine.Spy<(step: CorrectionStep, reason: CorrectionReason, token?: string) =>
    Observable<CorrectionResponse<string>>>;

  const preview = (paidAfter: number): CorrectionPreview => ({
    series: [{
      studentId: 1, studentName: 'Amine Belkacem', seriesId: 7, seriesName: 'Janvier', groupName: 'Math 1ère A',
      before: { cost: 4000, dueSoFar: 2000, paid: 3000, remaining: 1000, late: false },
      after: { cost: 4000, dueSoFar: 2000, paid: paidAfter, remaining: 4000 - paidAfter, late: paidAfter < 2000 }
    }],
    effects: [{ type: 'ENCASHMENT_CANCELLED', description: 'Reçu RECU-2030-0001 de 3 000,00 DA annulé' }],
    amountsUnchanged: false
  });

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    run = jasmine.createSpy('run');
    const data: CorrectionDialogData<string> = {
      titleKey: 'correction.cancel.title',
      subject: 'Reçu RECU-2030-0001 de 3 000,00 DA — Janvier (Math 1ère A)',
      reasons: ['DATA_ENTRY_ERROR', 'WRONG_AMOUNT', 'OTHER'],
      run,
      allowBack: true
    };
    await setupComponentTestBed(CorrectionDialogComponent, { providers: matDialogProviders(data, dialogRef) });
    fixture = TestBed.createComponent<CorrectionDialogComponent<string>>(CorrectionDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  const choose = (type: string, text = ''): void => {
    component.form.setValue({ reasonType: type, reasonText: text });
    fixture.detectChanges();
  };
  const text = (): string => (fixture.nativeElement as HTMLElement).textContent ?? '';
  const button = (selector: string): HTMLButtonElement | null =>
    (fixture.nativeElement as HTMLElement).querySelector(selector);

  it('ne propose aucun Motif par défaut, et n\'interroge rien sans lui', () => {
    expect(component.form.get('reasonType')!.value).toBeNull();
    expect(button('.cd-preview-button')!.disabled).toBeTrue();

    component.requestPreview();

    expect(run).not.toHaveBeenCalled();
  });

  it('exige un texte pour « Autre », espaces exclus', () => {
    choose('OTHER', '   ');
    expect(component.reasonComplete).toBeFalse();

    choose('OTHER', 'Doublon');
    expect(component.reasonComplete).toBeTrue();
  });

  it('demande l\'Aperçu avec le Motif choisi et l\'affiche', () => {
    run.and.returnValue(of({ preview: preview(0), previewToken: 'jeton-1', result: null }));
    choose('WRONG_AMOUNT', '  20 000 au lieu de 2 000 ');

    component.requestPreview();
    fixture.detectChanges();

    expect(run).toHaveBeenCalledWith('preview', { type: 'WRONG_AMOUNT', text: '20 000 au lieu de 2 000' });
    expect(text()).toContain('Reçu RECU-2030-0001 de 3 000,00 DA annulé');
    expect(button('.cd-confirm-button')).not.toBeNull();
    expect(button('.cd-preview-button')).toBeNull();
  });

  it('confirme exactement l\'Aperçu lu, avec son jeton, et rend le résultat', () => {
    run.and.returnValues(
      of({ preview: preview(0), previewToken: 'jeton-1', result: null }),
      of({ preview: preview(0), previewToken: 'jeton-1', result: 'annulé' }));
    choose('WRONG_AMOUNT');
    component.requestPreview();

    component.confirm();

    expect(run.calls.mostRecent().args).toEqual(['confirm', { type: 'WRONG_AMOUNT', text: null }, 'jeton-1']);
    expect(dialogRef.close).toHaveBeenCalledWith({ kind: 'confirmed', result: 'annulé' });
  });

  it('un autre Motif efface l\'Aperçu : le jeton ne vaut que pour le Motif lu', () => {
    run.and.returnValue(of({ preview: preview(0), previewToken: 'jeton-1', result: null }));
    choose('WRONG_AMOUNT');
    component.requestPreview();

    choose('DATA_ENTRY_ERROR');

    expect(component.preview).toBeNull();
    expect(component.previewToken).toBeNull();
    component.confirm();
    expect(run).toHaveBeenCalledTimes(1);
  });

  it('un Aperçu périmé est remplacé par le nouveau, à confirmer à son tour', () => {
    run.and.returnValues(
      of({ preview: preview(0), previewToken: 'jeton-1', result: null }),
      throwError(() => new CorrectionError('Les données ont changé', 409, 'STALE_PREVIEW', preview(500), 'jeton-2')));
    choose('WRONG_AMOUNT');
    component.requestPreview();

    component.confirm();
    fixture.detectChanges();

    expect(dialogRef.close).not.toHaveBeenCalled();
    expect(component.preview).toEqual(preview(500));
    expect(component.previewToken).toBe('jeton-2');
    expect(component.staleNotice).toBeTrue();
    expect(text()).toContain('correction.dialog.stale');
  });

  it('un refus s\'affiche tel que le serveur le rédige, remboursements nommés', () => {
    run.and.returnValue(throwError(() => new CorrectionError('Correction refusée : remboursement REMB-2030-0001.',
      409, 'REFUND_FLOOR', null, null,
      [{ refundNumber: 'REMB-2030-0001', refundDate: '2030-02-05T10:00:00', amount: 2000, seriesName: 'Janvier' }])));
    choose('WRONG_AMOUNT');

    component.requestPreview();
    fixture.detectChanges();

    expect(text()).toContain('Correction refusée : remboursement REMB-2030-0001.');
    expect(component.blockingRefunds.length).toBe(1);
    expect((fixture.nativeElement as HTMLElement).querySelectorAll('.cd-error li').length).toBe(1);
    expect(component.previewToken).toBeNull();
  });

  it('une erreur inattendue donne un message générique, sans Aperçu', () => {
    run.and.returnValue(throwError(() => new Error('réseau')));
    choose('WRONG_AMOUNT');

    component.requestPreview();

    expect(component.errorMessage).toBe('correction.dialog.error');
    expect(component.preview).toBeNull();
  });

  it('« Modifier » demande le retour à la saisie ; « Annuler » ferme sans rien', () => {
    component.back();
    expect(dialogRef.close).toHaveBeenCalledWith({ kind: 'back' });

    component.cancel();
    expect(dialogRef.close).toHaveBeenCalledWith();
  });
});
