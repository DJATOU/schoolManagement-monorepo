import { receiptFromEncashment } from './encashment-receipt';
import { anEncashment } from '../../testing/fixtures';

/**
 * Reçu réimprimé depuis l'historique (A.9) : il doit porter les mentions de l'original, toutes
 * tirées de l'Encaissement, et jamais celles du poste ou de l'instant de la réimpression.
 */
describe('receiptFromEncashment', () => {

  it('reprend le numéro, la date, l\'auteur et le montant de l\'Encaissement', () => {
    const receipt = receiptFromEncashment(anEncashment(), 'Espèces');

    expect(receipt.reference).toBe('RECU-2026-0042');
    expect(receipt.issuedAt.getTime()).toBe(new Date('2026-10-03T09:15:00').getTime());
    expect(receipt.adminUsername).toBe('caissier1');
    expect(receipt.amountPaid).toBe(5000);
    expect(receipt.studentName).toBe('Amina Belkacem');
    expect(receipt.groupName).toBe('Maths 1B');
    expect(receipt.seriesName).toBe('Octobre');
    expect(receipt.paymentMethodLabel).toBe('Espèces');
    expect(receipt.description).toBe('Versement du père');
    expect(receipt.isCatchUp).toBeFalse();
  });

  it('sépare la part imputée sur la série visée des parts reportées', () => {
    const receipt = receiptFromEncashment(anEncashment(), 'Espèces');

    expect(receipt.amountAllocated).toBe(3000);
    expect(receipt.carryOvers).toEqual([
      { seriesName: 'Novembre', amount: 1500 },
      { seriesName: 'Décembre', amount: 500 }
    ]);
  });

  it('imprime une part imputée nulle quand tout a été reporté', () => {
    // Série visée déjà soldée : le versement entier part sur la suivante.
    const receipt = receiptFromEncashment(anEncashment({
      amountReceived: 2000,
      allocations: [{ seriesId: 8, seriesName: 'Novembre', amount: 2000, carriedOver: true, active: true }]
    }), 'Espèces');

    expect(receipt.amountAllocated).toBe(0);
    expect(receipt.carryOvers).toEqual([{ seriesName: 'Novembre', amount: 2000 }]);
  });

  it('marque un rattrapage et omet une note absente', () => {
    const receipt = receiptFromEncashment(anEncashment({ kind: 'CATCH_UP', notes: null }), 'Espèces');

    expect(receipt.isCatchUp).toBeTrue();
    expect(receipt.description).toBeUndefined();
  });

  it('ne reprend pas la situation de la série, non conservée depuis le versement', () => {
    const receipt = receiptFromEncashment(anEncashment(), 'Espèces');

    expect(receipt.seriesTotalCost).toBeUndefined();
    expect(receipt.totalPaidAfter).toBeUndefined();
    expect(receipt.remainingAfter).toBeUndefined();
  });
});
