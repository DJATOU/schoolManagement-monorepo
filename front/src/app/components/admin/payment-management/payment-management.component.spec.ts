import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';

import { PaymentManagementComponent } from './payment-management.component';
import { setupComponentTestBed } from '../../../../testing/setup';

/** Espaces de groupement (U+202F en français) ramenées à une espace ordinaire. */
const plain = (text: string | null | undefined): string => (text ?? '').replace(/\s+/g, ' ').trim();

/**
 * Écran « Gestion des paiements » : après un remboursement, le montant affiché d'une ligne et le
 * statut de son versement sont nets de ce qui a été rendu (décision du propriétaire produit).
 *
 * <p>Le serveur calcule la part remboursée de chaque ligne et le statut net ; l'écran les affiche
 * sans recalcul. Les requêtes de filtres (groupes, élèves, séries, niveaux) restent en attente.</p>
 */
describe('PaymentManagementComponent', () => {
  let fixture: ComponentFixture<PaymentManagementComponent>;
  let component: PaymentManagementComponent;
  let http: HttpTestingController;

  /** Camille : trois séances à 800 DA, 400 DA rendus, imputés à la dernière ligne. */
  const rows = [
    {
      id: 5, studentFirstName: 'Camille', studentLastName: 'Amrani', studentId: 4,
      groupName: 'Anglais 1 AS', groupId: 2, seriesName: 'Anglais 1 AS - 10-2026-001', seriesId: 12,
      sessionName: 'Revision 3', amountPaid: 800, active: true, paymentId: 9, paymentStatus: 'IN_PROGRESS',
      refundedAmount: 400, netAmount: 400, paymentRefunded: 400
    },
    {
      id: 4, studentFirstName: 'Camille', studentLastName: 'Amrani', studentId: 4,
      groupName: 'Anglais 1 AS', groupId: 2, seriesName: 'Anglais 1 AS - 10-2026-001', seriesId: 12,
      sessionName: 'Revision 2', amountPaid: 800, active: true, paymentId: 9, paymentStatus: 'IN_PROGRESS',
      refundedAmount: 0, netAmount: 800, paymentRefunded: 400
    },
    {
      id: 7, studentFirstName: 'Nathan', studentLastName: 'Belhadj', studentId: 6,
      groupName: 'Anglais 1 AS', groupId: 2, seriesName: 'Anglais 1 AS - 10-2026-001', seriesId: 12,
      sessionName: 'Revision 2', amountPaid: 1200, active: true, paymentId: 11, paymentStatus: 'REFUNDED',
      refundedAmount: 1200, netAmount: 0, paymentRefunded: 1200
    },
    {
      // Réponse d'un serveur antérieur : sans champs de remboursement.
      id: 3, studentFirstName: 'Lina', studentLastName: 'Saadi', studentId: 8,
      groupName: 'Anglais 1 AS', groupId: 2, seriesName: 'Anglais 1 AS - 10-2026-001', seriesId: 12,
      sessionName: 'Revision 1', amountPaid: 800, active: true, paymentId: 13, paymentStatus: 'COMPLETED'
    }
  ];

  beforeEach(async () => {
    await setupComponentTestBed(PaymentManagementComponent);
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.use('fr');
    http = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(PaymentManagementComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();

    http.match(request => request.url.endsWith('/api/payment-details'))
      .forEach(request => request.flush({ content: rows, totalElements: rows.length }));
    fixture.detectChanges();
  });

  const amountCells = (): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('td.mat-column-amount')) as HTMLElement[];
  const statusCells = (): HTMLElement[] =>
    Array.from(fixture.nativeElement.querySelectorAll('td.mat-column-paymentStatus .payment-status')) as HTMLElement[];

  it('montre la ligne remboursée : versé barré, net en avant, part rendue', () => {
    const cell = amountCells()[0];

    expect(plain(cell.querySelector('.amount-gross')?.textContent)).toBe('800,00 DA');
    expect(plain(cell.querySelector('.amount-net')?.textContent)).toBe('400,00 DA');
    expect(plain(cell.querySelector('.amount-refunded-share')?.textContent)).toBe('400,00 DA remboursés');
  });

  it('laisse entière une ligne du même versement qui n\'a absorbé aucun remboursement', () => {
    const cell = amountCells()[1];

    expect(cell.querySelector('.amount-gross')).toBeNull();
    expect(plain(cell.textContent)).toBe('800,00 DA');
  });

  it('affiche les montants au format français', () => {
    expect(plain(amountCells()[2].querySelector('.amount-gross')?.textContent)).toBe('1 200,00 DA');
    expect(fixture.nativeElement.textContent).not.toContain('1,200');
  });

  it('reprend le statut net du serveur, « Remboursé » compris', () => {
    const [refundedLine, , fullyRefunded, untouched] = statusCells();

    expect(plain(refundedLine.textContent)).toBe('En cours');
    expect(plain(fullyRefunded.textContent)).toBe('Remboursé');
    expect(fullyRefunded.classList).toContain('payment-status--REFUNDED');
    expect(plain(untouched.textContent)).toBe('Soldé');
  });

  it('dit dans l\'info-bulle combien a été rendu sur le versement', () => {
    expect(plain(component.paymentStatusTooltip(rows[1])))
      .toBe('400,00 DA remboursés sur ce versement : statut calculé net des remboursements.');
    expect(component.paymentStatusTooltip(rows[3])).toBe('');
  });

  it('garde l\'info-bulle d\'annulation, prioritaire', () => {
    expect(component.paymentStatusTooltip({ ...rows[0], paymentStatus: 'CANCELLED' }))
      .toBe(frTranslations.payment.admin.paymentStatus.cancelledHint);
  });

  it('retombe sur le montant versé quand le serveur ne fournit pas le net', () => {
    expect(component.netAmountOf(rows[3])).toBe(800);
    expect(component.refundedOf(rows[3])).toBe(0);
    expect(component.hasRefundedShare(rows[3])).toBeFalse();
  });

  it('exporte le remboursé et le net en colonnes à part, le montant restant celui du versement', () => {
    let csv = '';
    spyOn(component as unknown as { downloadCsv: (content: string) => void }, 'downloadCsv')
      .and.callFake((content: string) => csv = content);

    component.exportToCSV();

    const [header, first, , , last] = csv.replace('\uFEFF', '').split('\r\n');
    expect(header).toContain('"Montant";"Remboursé";"Montant net"');
    expect(first).toContain('"800";"400";"400"');
    expect(last).toContain('"800";"0";"800"');
  });
});
