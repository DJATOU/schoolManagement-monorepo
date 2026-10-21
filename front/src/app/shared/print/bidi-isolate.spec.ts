import { bidiSegments, isolateLtrRuns, stripBidiControls } from './bidi-isolate';

/**
 * Isolement des passages latins d'un texte arabe : sans lui, l'algorithme bidirectionnel
 * réordonnait « 1.200,00 دج » et « 4 AM » dans un paragraphe de droite à gauche.
 */
describe('bidi-isolate', () => {
  /** Passages isolés d'un texte. */
  const runs = (text: string): string[] => bidiSegments(text).filter(s => s.ltr).map(s => s.text);

  it('un montant et son suffixe : seul le nombre est isolé', () => {
    expect(isolateLtrRuns('المبلغ : 1 200,00 دج')).toBe('المبلغ : <bdi dir="ltr">1 200,00</bdi> دج');
    expect(runs('12.500,00 دج')).toEqual(['12.500,00']);
  });

  it('un niveau « 4 AM » reste dans son ordre', () => {
    expect(runs('المستوى 4 AM')).toEqual(['4 AM']);
  });

  it('un numéro de reçu, une date et une heure', () => {
    expect(runs('رقم RECU-2030-0001')).toEqual(['RECU-2030-0001']);
    expect(runs('بتاريخ 05/01/2030 14:30')).toEqual(['05/01/2030 14:30']);
  });

  it('un nom latin suivi d\'une parenthèse fermée dans le passage', () => {
    expect(runs('التلميذ: Ahmed Benali (G1)')).toEqual(['Ahmed Benali (G1)']);
  });

  it('une parenthèse ouverte hors du passage reste dans la phrase', () => {
    expect(runs('Amine Belkacem  (استدراك)')).toEqual(['Amine Belkacem']);
    expect(runs('(الفوج G1)')).toEqual(['G1']);
  });

  it('la ponctuation qui suit un passage reste dans la phrase', () => {
    expect(isolateLtrRuns('استُبدل بالوصل RECU-2030-0002.')).toBe('استُبدل بالوصل <bdi dir="ltr">RECU-2030-0002</bdi>.');
  });

  it('une date ar-DZ : les marques de direction d\'Intl sont retirées, la date isolée', () => {
    const date = new Intl.DateTimeFormat('ar-DZ', { dateStyle: 'short' }).format(new Date(2030, 0, 5));
    expect(stripBidiControls(date)).toBe('5/1/2030');
    expect(runs(`التاريخ: ${date}`)).toEqual(['5/1/2030']);
  });

  it('un signe et un pourcentage font partie du passage', () => {
    expect(runs('المبلغ -400,00 دج')).toEqual(['-400,00']);
    expect(runs('النسبة Standard (60 %)')).toEqual(['Standard (60 %)']);
  });

  it('phrase arabe : un tiret entre deux valeurs les isole chacune, dans l\'ordre de la phrase', () => {
    expect(runs('نسخة ثانية رقم 2 — 6/10/2030، 11:20 ص')).toEqual(['2', '6/10/2030', '11:20']);
    expect(runs('دفعة 20/10/2029 — 1.200,00 دج — Trop-perçu (1 séance)'))
      .toEqual(['20/10/2029', '1.200,00', 'Trop-perçu (1 séance)']);
  });

  it('phrase arabe : après des lettres latines, le tiret fait partie de la donnée', () => {
    expect(runs('Séance 7 — 4 AM  (استدراك)')).toEqual(['Séance 7 — 4 AM']);
  });

  it('calcul dans une phrase arabe : chaque nombre isolé, « × » et « = » restent dans la phrase', () => {
    expect(runs('72.000,00 دج × 60 % = 43.200,00 دج')).toEqual(['72.000,00', '60 %', '43.200,00']);
  });

  it('deux-points suivis d\'une espace : séparateur dans une phrase arabe ; une heure n\'en est pas un', () => {
    expect(runs('الوثيقة REMB-2030-0007 بتاريخ 20/10/2029: 1.200,00 دج')).toEqual(['REMB-2030-0007', '20/10/2029', '1.200,00']);
    expect(runs('بتاريخ 05/01/2030 14:30')).toEqual(['05/01/2030 14:30']);
    expect(runs('Séance du 28/01/2030 (Maths 4 AM A) : Amine Belkacem')).toEqual(['Séance du 28/01/2030 (Maths 4 AM A) : Amine Belkacem']);
  });

  it('texte sans lettre arabe : un seul passage, tirets compris', () => {
    expect(runs('Maths 4 AM A — Séance 3')).toEqual(['Maths 4 AM A — Séance 3']);
  });

  it('un texte entièrement arabe n\'est pas touché', () => {
    expect(isolateLtrRuns('وصل دفع')).toBe('وصل دفع');
    expect(bidiSegments('وصل دفع')).toEqual([{ text: 'وصل دفع', ltr: false }]);
  });

  it('un saut de ligne coupe le passage', () => {
    expect(runs('Amine\nBelkacem')).toEqual(['Amine', 'Belkacem']);
  });

  it('échappe tout le HTML, dans les passages comme autour', () => {
    expect(isolateLtrRuns('<script>alert("x")</script> & اسم'))
      .toBe('&lt;<bdi dir="ltr">script&gt;alert(&quot;x&quot;)&lt;/script</bdi>&gt; &amp; اسم');
    expect(isolateLtrRuns('<img src=x onerror=alert(1)>')).not.toContain('<img');
  });
});
