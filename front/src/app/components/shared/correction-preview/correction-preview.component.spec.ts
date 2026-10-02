import { ComponentFixture, TestBed } from '@angular/core/testing';

import { CorrectionPreviewComponent } from './correction-preview.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { CorrectionPreview } from '../../../models/correction/correction';

/**
 * L'Aperçu d'une correction (spec admin-corrections, exigences 4.1, 4.2, 4.4) : montants avant et
 * après par Série, changement marqué autrement que par la couleur, et « aucun montant ne change »
 * dit en toutes lettres.
 */
describe('CorrectionPreviewComponent', () => {
  let fixture: ComponentFixture<CorrectionPreviewComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(CorrectionPreviewComponent);
    fixture = TestBed.createComponent(CorrectionPreviewComponent);
  });

  function show(preview: CorrectionPreview): HTMLElement {
    fixture.componentRef.setInput('preview', preview);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  const cancellation: CorrectionPreview = {
    series: [{
      studentId: 1, studentName: 'Amine Belkacem', seriesId: 7, seriesName: 'Janvier', groupName: 'Math 1ère A',
      before: { cost: 4000, dueSoFar: 2000, paid: 3000, remaining: 1000, late: false },
      after: { cost: 4000, dueSoFar: 2000, paid: 0, remaining: 4000, late: true }
    }],
    effects: [
      { type: 'ENCASHMENT_CANCELLED', description: 'Reçu RECU-2030-0001 de 3 000,00 DA annulé' },
      { type: 'ALLOCATION_NEUTRALIZED', description: 'Imputation de 3 000,00 DA sur « Janvier » neutralisée' }
    ],
    amountsUnchanged: false
  };

  it('nomme chaque Série, son groupe et son élève', () => {
    expect(show(cancellation).querySelector('.cp-series-title')?.textContent)
      .toContain('Janvier (Math 1ère A) — Amine Belkacem');
  });

  it('montre chaque montant avant et après, et ne marque que ceux qui changent', () => {
    const rows = Array.from(show(cancellation).querySelectorAll('.cp-table tbody tr'));

    // Coût, dû à ce jour, versé, reste, statut.
    expect(rows.length).toBe(5);
    const changed = rows.map(row => row.classList.contains('cp-changed'));
    expect(changed).toEqual([false, false, true, true, true]);
    expect(rows[2].textContent).toContain('3,000.00 DA');
    expect(rows[2].querySelector('.cp-arrow')).not.toBeNull();
    expect(rows[0].querySelector('.cp-arrow')).toBeNull();
  });

  it('écrit le passage « à jour → en retard » en toutes lettres', () => {
    const status = Array.from(show(cancellation).querySelectorAll('.cp-table tbody tr'))[4];

    expect(status.textContent).toContain('correction.preview.upToDate');
    expect(status.textContent).toContain('correction.preview.late');
  });

  it('liste les autres effets', () => {
    const items = Array.from(show(cancellation).querySelectorAll('.cp-effects li')).map(li => li.textContent?.trim());

    expect(items).toEqual([
      'Reçu RECU-2030-0001 de 3 000,00 DA annulé',
      'Imputation de 3 000,00 DA sur « Janvier » neutralisée'
    ]);
  });

  it('dit qu\'aucun montant ne change, sans tableau vide', () => {
    const element = show({
      series: [],
      effects: [{ type: 'ENCASHMENT_DETAILS_EDITED', description: 'Reçu RECU-2030-0001 : mode espèces → chèque' }],
      amountsUnchanged: true
    });

    expect(element.querySelector('.cp-unchanged')?.textContent).toContain('correction.preview.amountsUnchanged');
    expect(element.querySelector('.cp-table')).toBeNull();
    expect(element.querySelector('.cp-effects')).not.toBeNull();
  });

  it('une Série sans groupe est nommée sans parenthèses', () => {
    const element = show({ ...cancellation, series: [{ ...cancellation.series[0], groupName: null }] });

    expect(element.querySelector('.cp-series-title')?.textContent?.trim()).toBe('Janvier — Amine Belkacem');
  });
});
