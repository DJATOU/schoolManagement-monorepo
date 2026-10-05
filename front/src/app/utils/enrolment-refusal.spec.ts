import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';

import { enrolmentRefusalMessage } from './enrolment-refusal';
import { setupServiceTestBed } from '../../testing/setup';

/**
 * Le refus d'une inscription dit sa cause (C.8) : le message du serveur nomme l'année et ses
 * bornes, ou le départ recouvert ; le message générique d'avant laissait deviner.
 */
describe('enrolmentRefusalMessage', () => {
  let translate: TranslateService;

  beforeEach(() => {
    setupServiceTestBed();
    translate = TestBed.inject(TranslateService);
  });

  it('rend le message du serveur tel quel', () => {
    const message = 'La date d\'arrivée du 01/07/2030 est hors de l\'année scolaire 2029-2030 (du 01/09/2029 au 30/06/2030).';

    expect(enrolmentRefusalMessage({ status: 400, error: { message } }, translate)).toBe(message);
  });

  it('nomme les groupes déjà suivis, lus dans « alreadyAssociatedEntities »', () => {
    const error = { status: 409, error: { message: 'Some entities were already associated',
      alreadyAssociatedEntities: ['Math 1ère A', 'Physique 1ère A'] } };

    expect(enrolmentRefusalMessage(error, translate)).toBe('enrolment.add.alreadyEnrolled');
    const instant = spyOn(translate, 'instant').and.callThrough();
    enrolmentRefusalMessage(error, translate);
    expect(instant).toHaveBeenCalledWith('enrolment.add.alreadyEnrolled', { names: 'Math 1ère A, Physique 1ère A' });
  });

  it('un 409 sans groupe nommé rend le message du serveur (départ recouvert)', () => {
    const message = 'Amine Belkacem a déjà été inscrit au groupe « Math 1ère A » … rouvrez cette inscription.';

    expect(enrolmentRefusalMessage({ status: 409, error: { message, alreadyAssociatedEntities: [] } }, translate))
      .toBe(message);
  });

  it('un élément introuvable rend le champ « error » du serveur', () => {
    expect(enrolmentRefusalMessage({ status: 404, error: { error: 'Group not found with id: 9' } }, translate))
      .toBe('Group not found with id: 9');
  });

  it('sans message exploitable : serveur injoignable, ou message générique', () => {
    expect(enrolmentRefusalMessage({ status: 0, error: null }, translate)).toBe('enrolment.add.unreachable');
    expect(enrolmentRefusalMessage({ status: 500, error: { message: '   ' } }, translate)).toBe('enrolment.add.error');
    expect(enrolmentRefusalMessage(null, translate)).toBe('enrolment.add.error');
  });
});
