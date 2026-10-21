import { TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { TDocumentDefinitions } from 'pdfmake/interfaces';

import { AttendanceHistoryDialogComponent } from '../app/components/attendance/attendance-history-dialog/attendance-history-dialog.component';
import { PaymentHistoryDialogComponent } from '../app/components/payment/payment-history/payment-history-dialog/payment-history-dialog.component';
import { StudentFullHistoryDTO } from '../app/components/student/domain/StudentFullHistoryDTO';
import { PdfGeneratorService } from '../app/components/student/services/pdf-generator.service';
import { CorrectionJournal, JournalCategory, JournalEntry } from '../app/models/correction/journal';
import { SessionHistoryDTO } from '../app/models/session/SessionHistoryDTO';
import { SessionSeries } from '../app/models/sessionSerie/sessionSerie';
import { RefundReceipt, StudentRefund } from '../app/models/refund/refund';
import { formatAmount } from '../app/pipes/amount.pipe';
import { CorrectionJournalPdfService } from '../app/services/correction-journal-pdf.service';
import { PaymentReceiptPdfService } from '../app/services/payment-receipt-pdf.service';
import { PayoutSlipPdfService } from '../app/services/payout-slip-pdf.service';
import { ProfilePdfOptions, ProfilePdfService } from '../app/services/profile-pdf.service';
import { RefundReceiptPdfService } from '../app/services/refund-receipt-pdf.service';
import { SeriesSessionsPdfService } from '../app/services/series-sessions-pdf.service';
import { SessionAttendancePdfService } from '../app/services/session-attendance-pdf.service';
import { pdfWithCurrency } from '../app/shared/currency';
import { inPrintLanguage, pdfInstant, pdfLanguage, pdfLocale } from '../app/shared/pdf-i18n';
import { genderLabelKey } from '../app/utils/form-options';
import { payout, slip } from './payroll-fixtures';

/**
 * Chaque document imprimable de l'application, composé par son vrai générateur à partir de données
 * réalistes : noms latins (« Amine Belkacem »), niveaux « 4 AM », montants (12 500, 1 200), numéros
 * de reçu, dates, tableaux sur plusieurs pages.
 *
 * <p>À appeler dans la langue du document voulue (`withPrintLanguage`), traductions chargées : les
 * libellés, les dates et les montants sont ceux que l'utilisateur obtiendrait.</p>
 */
export interface PrintFixture {
  /** Nom de fichier du rendu (`<name>.html`). */
  name: string;
  /** Titre de la page (nom proposé par « Enregistrer au format PDF »). */
  title: string;
  doc: TDocumentDefinitions;
}

const FIRST_NAMES = ['Amine', 'Yasmine', 'Karim', 'Lina', 'Rayan', 'Inès', 'Sofiane', 'Meriem', 'Walid', 'Nour',
  'Hichem', 'Sara', 'Anis', 'Imane', 'Bilal', 'Aya', 'Mehdi', 'Rania'];
const LAST_NAMES = ['Belkacem', 'Benali', 'Haddad', 'Mansouri', 'Ait Ahmed', 'Bouzid', 'Cherif', 'Saidi', 'Khelifi',
  'Ziani', 'Boudiaf', 'Hamidi'];

/** « Amine Belkacem », « Yasmine Benali »… */
export function fixtureName(i: number): string {
  return `${FIRST_NAMES[i % FIRST_NAMES.length]} ${LAST_NAMES[(i * 5) % LAST_NAMES.length]}`;
}

/** Tous les documents, dans la langue du document courante. */
export function buildPrintFixtures(logo: string): PrintFixture[] {
  return [
    studentProfile(),
    teacherProfile(),
    groupProfile(),
    datatableExport(),
    paymentReceipt(logo),
    paymentReceiptCancelled(logo),
    refundReceipt(logo),
    payoutSlip(logo),
    payoutSlipCancelled(logo),
    correctionJournal(logo),
    attendanceSheet(),
    seriesSessions(),
    fullHistory(logo),
    attendanceHistory(logo),
    paymentHistory(logo)
  ];
}

function translate(): TranslateService {
  return TestBed.inject(TranslateService);
}

function t(key: string, params?: object): string {
  return pdfInstant(translate(), key, params);
}

/** Montant comme les écrans appelants le formatent pour un document (`group-profile`). */
function money(value: number): string {
  return pdfWithCurrency(value.toLocaleString(pdfLocale(translate()), { maximumFractionDigits: 2 })
    .replace(/[\u202F\u00A0]/g, ' '), translate());
}

function profile(name: string, options: ProfilePdfOptions): PrintFixture {
  return { name, title: options.title, doc: TestBed.inject(ProfilePdfService).buildDocument(options) };
}

/** Fiche élève, comme `StudentProfileComponent.printProfilePdf`. */
function studentProfile(): PrintFixture {
  return profile('student-profile', {
    title: 'Amine Belkacem',
    subtitle: t('PROFILE_PDF.STUDENT_WITH_LEVEL', { level: '4 AM' }),
    sections: [
      {
        heading: t('STUDENT_PROFILE.PERSONAL_INFO'),
        rows: [
          { label: t('COMMON.GENDER'), value: t(genderLabelKey('male')!) },
          { label: t('COMMON.EMAIL'), value: 'amine.belkacem@example.org' },
          { label: t('STUDENT_PROFILE.PHONE'), value: '0555 12 34 56' },
          { label: t('COMMON.BIRTHDATE'), value: '14/03/2011' },
          { label: t('COMMON.BIRTHPLACE'), value: 'Alger' }
        ]
      },
      {
        heading: t('STUDENT_PROFILE.ACADEMIC_INFO'),
        rows: [
          { label: t('COMMON.LEVEL'), value: '4 AM' },
          { label: t('COMMON.SCHOOL'), value: 'CEM Ibn Khaldoun' },
          { label: t('STUDENT_PROFILE.AVERAGE'), value: 15.5 }
        ]
      },
      {
        heading: t('tutor.title'),
        rows: [
          { label: t('tutor.name'), value: 'Karim Belkacem' },
          { label: t('tutor.relationship'), value: 'Père' },
          { label: t('tutor.phone'), value: '0661 98 76 54' },
          { label: t('tutor.email'), value: null }
        ]
      }
    ],
    tableTitle: t('STUDENT_PROFILE.GROUPS_COUNT', { count: 2 }),
    tableColumns: ['#', t('STUDENT_PROFILE.PDF.GROUP_COLUMN'), t('COMMON.LEVEL'), t('STUDENT_PROFILE.PDF.SUBJECT_COLUMN')],
    tableRows: [[1, 'Maths 4 AM A', '4 AM', 'Mathématiques'], [2, 'Physique 4 AM B', '4 AM', 'Physique']]
  });
}

/** Fiche enseignant, comme `TeacherProfileComponent.printProfilePdf`. */
function teacherProfile(): PrintFixture {
  return profile('teacher-profile', {
    title: 'Nadia Aït Ahmed',
    subtitle: t('PROFILE_PDF.TEACHER_WITH_SPECIALIZATION', { specialization: 'Mathématiques' }),
    sections: [
      {
        heading: t('TEACHER_PROFILE.PERSONAL_INFO'),
        rows: [
          { label: t('COMMON.GENDER'), value: t(genderLabelKey('female')!) },
          { label: t('COMMON.EMAIL'), value: 'nadia.aitahmed@example.org' },
          { label: t('TEACHER_PROFILE.PHONE'), value: '0770 11 22 33' },
          { label: t('COMMON.BIRTHDATE'), value: '02/09/1985' },
          { label: t('COMMON.BIRTHPLACE'), value: 'Tizi Ouzou' },
          { label: t('COMMON.ADDRESS'), value: '12 rue Didouche Mourad, Alger' }
        ]
      },
      {
        heading: t('TEACHER_PROFILE.PROFESSIONAL_INFO'),
        rows: [
          { label: t('TEACHER_PROFILE.SPECIALIZATION'), value: 'Mathématiques' },
          { label: t('TEACHER_PROFILE.EXPERIENCE'), value: 12 }
        ]
      }
    ],
    listTitle: t('TEACHER_PROFILE.GROUPS_COUNT', { count: 3 }),
    listItems: ['Maths 4 AM A — 4 AM (Mathématiques)', 'Maths 3 AM B — 3 AM (Mathématiques)', 'Soutien BAC — 3 AS']
  });
}

/** Fiche groupe et ses volets (encaissements, séries), comme `GroupProfileComponent.printGroupPdf`. */
function groupProfile(): PrintFixture {
  const students = Array.from({ length: 24 }, (_, i) => fixtureName(i).split(' '));
  const series = ['Septembre', 'Octobre', 'Novembre', 'Décembre'];
  return profile('group-profile', {
    title: 'Maths 4 AM A',
    subtitle: t('PROFILE_PDF.GROUP_WITH_TYPE', { type: 'Petit groupe' }),
    sections: [{
      heading: t('GROUP_PROFILE.INFO_TITLE'),
      rows: [
        { label: t('GROUP_PROFILE.GROUP_TYPE'), value: 'Petit groupe' },
        { label: t('COMMON.LEVEL'), value: '4 AM' },
        { label: t('GROUP_PROFILE.SUBJECT'), value: 'Mathématiques' },
        { label: t('GROUP_PROFILE.PDF.SCHOOL_YEAR'), value: '2029-2030' },
        { label: t('GROUP_PROFILE.SESSIONS_PER_SERIES'), value: 8 },
        { label: t('GROUP_PROFILE.PRICE'), value: 1200 },
        { label: t('GROUP_PROFILE.TEACHER'), value: 'Nadia Aït Ahmed' }
      ]
    }],
    tableTitle: t('GROUP_PROFILE.PDF.STUDENTS_TABLE', { count: students.length }),
    tableColumns: ['#', t('COMMON.LAST_NAME'), t('COMMON.FIRST_NAME'), t('GROUP_PROFILE.PDF.REMARKS')],
    tableRows: students.map(([first, ...last], i) => [i + 1, last.join(' '), first, '']),
    extraPages: [
      {
        heading: t('revenue.title'),
        subtitle: 'Maths 4 AM A',
        sections: [{
          heading: t('GROUP_PROFILE.PDF.SUMMARY'),
          rows: [
            { label: t('GROUP_PROFILE.PDF.COLLECTED_NET'), value: money(112500) },
            { label: t('revenue.expected'), value: money(124800) },
            { label: t('revenue.remaining'), value: money(12300) },
            { label: t('GROUP_PROFILE.PDF.REFUNDED'), value: money(1200) }
          ]
        }],
        tables: [
          {
            title: t('revenue.bySeries'),
            columns: ['#', t('GROUP_PROFILE.PDF.SERIES_COLUMN'), t('revenue.collected'), t('revenue.expected'),
              t('GROUP_PROFILE.PDF.REMAINING_COLUMN'), t('revenue.overpaid')],
            rows: series.map((name, i) => [i + 1, name, money(28800 - i * 1200), money(31200), money(2400 + i * 1200), i === 0 ? money(1200) : '']),
            note: t('GROUP_PROFILE.PDF.BY_SERIES_NOTE')
          },
          {
            title: t('revenue.byMonth'),
            columns: ['#', t('GROUP_PROFILE.PDF.MONTH_COLUMN'), t('revenue.collected')],
            rows: [9, 10, 11, 12].map((month, i) => [i + 1,
              new Date(2029, month - 1, 1).toLocaleDateString(pdfLocale(translate()), { month: 'long', year: 'numeric' }),
              money(12500 + i * 1200)]),
            note: t('GROUP_PROFILE.PDF.BY_MONTH_NOTE')
          }
        ]
      },
      {
        heading: t('GROUP_PROFILE.SERIES'),
        subtitle: 'Maths 4 AM A',
        emptyMessage: t('revenue.noSeries'),
        tables: [{
          title: t('GROUP_PROFILE.PDF.SERIES_TABLE', { count: series.length }),
          columns: ['#', t('GROUP_PROFILE.PDF.SERIES_COLUMN'), t('GROUP_PROFILE.PDF.START'), t('GROUP_PROFILE.PDF.END'),
            t('GROUP_PROFILE.PDF.SESSIONS_CREATED'), t('GROUP_PROFILE.PDF.PLANNED'), t('GROUP_PROFILE.PDF.VALIDATED')],
          rows: series.map((name, i) => [i + 1, name,
            new Intl.DateTimeFormat(pdfLocale(translate())).format(new Date(2029, 8 + i, 1)),
            new Intl.DateTimeFormat(pdfLocale(translate())).format(new Date(2029, 8 + i, 28)), 8, 8, i < 3 ? 8 : 5])
        }]
      }
    ]
  });
}

/**
 * Export d'une liste (tarifs, 45 lignes), comme `ReusableDatatableComponent.onPrint` : les cellules
 * sont produites l'écran momentanément dans la langue du document.
 */
function datatableExport(): PrintFixture {
  const lang = pdfLanguage(translate());
  const printed = inPrintLanguage(translate(), lang, () => {
    const title = t('DATATABLE.ENTITIES.pricing.title');
    return {
      title,
      subtitle: t('DATATABLE.PRINT_SUBTITLE_ALL', { count: 45 }),
      columns: ['id', 'price', 'effectiveDate', 'expirationDate', 'description'].map(c => t(`pricing.table.${c}`)),
      rows: Array.from({ length: 45 }, (_, i) => [
        String(i + 1),
        `${[1200, 12500, 1500, 2000, 800][i % 5]} ${translate().instant('COMMON.CURRENCY_SUFFIX')}`,
        new Intl.DateTimeFormat(pdfLocale(translate())).format(new Date(2029, 8, 1 + (i % 28))),
        new Intl.DateTimeFormat(pdfLocale(translate())).format(new Date(2030, 5, 30)),
        `Tarif ${['Petit groupe', 'Individuel', 'Grand groupe'][i % 3]} 4 AM`
      ])
    };
  });
  return profile('datatable-export', {
    title: printed.title,
    subtitle: printed.subtitle,
    sections: [],
    tableTitle: printed.title,
    tableColumns: printed.columns,
    tableRows: printed.rows
  });
}

/** Reçu de versement avec report sur la série suivante. */
function paymentReceipt(logo: string): PrintFixture {
  const service = TestBed.inject(PaymentReceiptPdfService);
  return {
    name: 'payment-receipt',
    title: 'RECU-2030-0042',
    doc: service.buildDocument({
      reference: 'RECU-2030-0042',
      issuedAt: new Date(2030, 1, 3, 9, 15),
      studentName: 'Amine Belkacem',
      groupName: 'Maths 4 AM A',
      seriesName: 'Octobre',
      amountPaid: 12500,
      paymentMethodLabel: t('payment.dialog.methods.cash'),
      description: 'Versement de la famille (2 mois)',
      isCatchUp: false,
      seriesTotalCost: 9600,
      billableSessions: 8,
      totalPaidAfter: 9600,
      remainingAfter: 0,
      amountAllocated: 8400,
      carryOvers: [{ seriesName: 'Novembre', amount: 4100 }],
      adminUsername: 'caissier1'
    }, logo)
  };
}

/** Reçu réimprimé d'un versement annulé et remplacé : tampon sur toute la page. */
function paymentReceiptCancelled(logo: string): PrintFixture {
  const service = TestBed.inject(PaymentReceiptPdfService);
  return {
    name: 'payment-receipt-cancelled',
    title: 'RECU-2030-0043',
    doc: service.buildDocument({
      reference: 'RECU-2030-0043',
      issuedAt: new Date(2030, 1, 4, 16, 40),
      studentName: 'Yasmine Benali',
      groupName: 'Physique 4 AM B',
      seriesName: 'Novembre',
      amountPaid: 1200,
      paymentMethodLabel: t('payment.dialog.methods.cash'),
      isCatchUp: true,
      seriesTotalCost: 1200,
      billableSessions: 1,
      totalPaidAfter: 1200,
      remainingAfter: 0,
      amountAllocated: 1200,
      carryOvers: [],
      adminUsername: 'caissier2',
      cancellation: { cancelledAt: new Date(2030, 1, 5, 10, 0), replacedBy: 'RECU-2030-0044' }
    }, logo)
  };
}

/** Reçu de remboursement réimprimé : mention « duplicata » et son rang. */
function refundReceipt(logo: string): PrintFixture {
  const receipt: RefundReceipt = {
    refundId: 7,
    refundNumber: 'REMB-2030-0007',
    refundDate: new Date(2030, 9, 5, 10, 0),
    amount: 1200,
    reason: 'Trop-perçu sur la série d\'octobre (1 séance)',
    studentFirstName: 'Amine',
    studentLastName: 'Belkacem',
    paymentDate: new Date(2030, 9, 1, 9, 0),
    amountPaid: 12500,
    groupName: 'Maths 4 AM A',
    seriesName: 'Octobre',
    recordedBy: 'directrice',
    issuanceRank: 2,
    issuedAt: '2030-10-06T11:20:00',
    fileName: 'remboursement-REMB-2030-0007.pdf'
  };
  return { name: 'refund-receipt', title: 'remboursement-REMB-2030-0007', doc: TestBed.inject(RefundReceiptPdfService).buildDocument(receipt, logo) };
}

/** Bordereau de paie, duplicata. */
function payoutSlip(logo: string): PrintFixture {
  return {
    name: 'payout-slip',
    title: 'paie-2030-0001',
    doc: TestBed.inject(PayoutSlipPdfService).buildDocument(slip({ issuanceRank: 2, issuedAt: '2030-02-03T08:05:00',
      payout: payout({ note: 'Remis en main propre, Maths 4 AM A' }) }), logo)
  };
}

/** Bordereau d'une retenue annulée, remplacée. */
function payoutSlipCancelled(logo: string): PrintFixture {
  return {
    name: 'payout-slip-cancelled',
    title: 'paie-2030-0009',
    doc: TestBed.inject(PayoutSlipPdfService).buildDocument(slip({
      payout: payout({
        id: 49, payoutNumber: 'PAIE-2030-0009', kind: 'REGULARIZATION', initialPayoutNumber: 'PAIE-2030-0001',
        baseDelta: -2000, teacherAmount: -1200, schoolAmount: -800, status: 'CANCELLED',
        cancelledAt: '2030-03-02T09:00:00', cancelledBy: 'directrice', cancelReasonType: 'DATA_ENTRY_ERROR',
        cancelReasonText: 'Montant saisi deux fois', replacedByNumber: 'PAIE-2030-0010'
      })
    }), logo)
  };
}

/** Journal des corrections, 30 entrées, paysage. */
function correctionJournal(logo: string): PrintFixture {
  const categories: JournalCategory[] = ['ENCASHMENT', 'ENROLMENT', 'ATTENDANCE', 'JUSTIFICATION', 'CATCH_UP'];
  const entries: JournalEntry[] = Array.from({ length: 30 }, (_, i) => {
    const category = categories[i % categories.length];
    const day = String(28 - (i % 28)).padStart(2, '0');
    return {
      performedAt: `2030-01-${day}T${String(8 + (i % 10)).padStart(2, '0')}:30:00`,
      category,
      description: `Séance du ${day}/01/2030 (Maths 4 AM A) : Amine Belkacem absent → présent`,
      amountEffect: category === 'JUSTIFICATION' ? null : `Janvier (Maths 4 AM A) : dû à ce jour 0,00 → 1 200,00 DA`,
      reasonType: category === 'JUSTIFICATION' ? null : 'DATA_ENTRY_ERROR',
      reasonText: i % 3 === 0 ? 'Feuille de présence mal saisie' : null,
      performedBy: i % 2 ? 'directrice' : 'caissier1'
    };
  });
  const journal: CorrectionJournal = { studentId: 42, studentName: 'Amine Belkacem', from: '2030-01-01', to: '2030-01-31', entries };
  return {
    name: 'correction-journal',
    title: 'journal_Amine_Belkacem',
    doc: TestBed.inject(CorrectionJournalPdfService).buildDocument(journal, logo, new Date(2030, 1, 1, 14, 30))
  };
}

/** Feuille de présence validée, 35 élèves (deux pages). */
function attendanceSheet(): PrintFixture {
  const students = Array.from({ length: 35 }, (_, i) => ({
    fullName: fixtureName(i),
    isPresent: i % 4 !== 0,
    isJustified: i % 8 === 0,
    isCatchUp: i === 5,
    note: i % 7 === 0 ? 'Arrivé en retard (15 min)' : ''
  }));
  return {
    name: 'attendance-sheet',
    title: 'Maths 4 AM A — Séance 3',
    doc: TestBed.inject(SessionAttendancePdfService).buildDocument({
      title: 'Maths 4 AM A — Séance 3',
      sessionType: 'Cours',
      groupName: 'Maths 4 AM A',
      roomName: 'Salle 2',
      teacherName: 'Nadia Aït Ahmed',
      sessionTimeStart: new Date(2030, 0, 5, 14, 30),
      sessionTimeEnd: new Date(2030, 0, 5, 16, 0),
      isFinished: true
    }, students)
  };
}

/** Séances d'une série, 20 séances. */
function seriesSessions(): PrintFixture {
  const sessions = Array.from({ length: 20 }, (_, i) => ({
    title: `Séance ${i + 1} — Maths 4 AM A`,
    sessionType: i % 5 === 4 ? 'Rattrapage' : 'Cours',
    teacherName: 'Nadia Aït Ahmed',
    roomName: `Salle ${1 + (i % 3)}`,
    sessionTimeStart: new Date(2030, 0, 5 + i, 14, 30),
    sessionTimeEnd: new Date(2030, 0, 5 + i, 16, 0),
    isFinished: i < 12
  }));
  return {
    name: 'series-sessions',
    title: 'Octobre',
    doc: TestBed.inject(SeriesSessionsPdfService).buildDocument({
      name: 'Octobre', groupName: 'Maths 4 AM A', totalSessions: 20, sessionsCompleted: 12, numberOfSessionsCreated: 20,
      serieTimeStart: new Date(2029, 9, 1), serieTimeEnd: new Date(2029, 9, 31)
    }, sessions)
  };
}

function historySession(i: number, overrides: Partial<SessionHistoryDTO> = {}): SessionHistoryDTO {
  return {
    catchUpSession: false,
    sessionId: 100 + i,
    sessionName: `Séance ${i + 1}`,
    sessionDate: `2029-10-${String(2 + i * 3).padStart(2, '0')}T14:30:00`,
    attendanceStatus: i % 4 === 3 ? 'ABSENT' : 'PRESENT',
    isJustified: i % 8 === 3,
    description: i === 2 ? 'Exercices 4 AM, chapitre 3' : '',
    paymentStatus: i < 6 ? 'PAID' : 'UNPAID',
    amountPaid: i < 6 ? 1200 : 0,
    paymentDate: '2029-10-01T10:00:00',
    billable: true,
    inclusionReason: 'AFTER_ENROLMENT',
    ...overrides
  };
}

function studentHistory(): StudentFullHistoryDTO {
  return {
    studentId: 42,
    studentName: 'Amine Belkacem',
    catchUp: false,
    groups: [
      {
        groupId: 3, groupName: 'Maths 4 AM A', catchUp: false,
        series: [
          {
            seriesId: 12, seriesName: 'Octobre', paymentStatus: 'PARTIAL', totalAmountPaid: 7200, totalCost: 9600,
            totalAllocated: 7200, totalOverpaid: 0, totalRefunded: 1200, billableSessions: 8, unitPriceNet: 1200,
            sessions: Array.from({ length: 8 }, (_, i) => historySession(i))
          },
          {
            seriesId: 13, seriesName: 'Novembre', paymentStatus: 'FULL', totalAmountPaid: 12500, totalCost: 9600,
            totalAllocated: 9600, totalOverpaid: 2900, totalRefunded: 0, billableSessions: 8, unitPriceNet: 1200,
            sessions: Array.from({ length: 8 }, (_, i) => historySession(i + 8, {
              sessionDate: `2029-11-${String(2 + i * 3).padStart(2, '0')}T14:30:00`, paymentStatus: 'PAID', amountPaid: 1200
            }))
          }
        ]
      },
      {
        groupId: 4, groupName: 'Physique 4 AM B', catchUp: false,
        series: [{
          seriesId: 21, seriesName: 'Octobre', paymentStatus: 'UNPAID', totalAmountPaid: 0, totalCost: 2400,
          totalAllocated: 0, totalOverpaid: 0, totalRefunded: 0, billableSessions: 2, unitPriceNet: 1200,
          sessions: [
            historySession(30, { paymentStatus: 'UNPAID', amountPaid: 0 }),
            historySession(31, { attendanceStatus: 'ABSENT', billable: false, inclusionReason: 'EXCLUDED', paymentStatus: 'UNPAID', amountPaid: 0 }),
            historySession(32, { catchUpSession: true, inclusionReason: 'ATTENDED_BEFORE_ENROLMENT', paymentStatus: 'UNPAID', amountPaid: 0 })
          ]
        }]
      }
    ]
  };
}

/** Historique complet de l'élève, deux groupes (une page chacun). */
function fullHistory(logo: string): PrintFixture {
  const refunds: StudentRefund[] = [{
    refundId: 7, refundNumber: 'REMB-2030-0007', refundDate: '2029-10-20', amount: 1200,
    reason: 'Trop-perçu (1 séance)', seriesId: 12, seriesName: 'Octobre', groupId: 3, groupName: 'Maths 4 AM A',
    recordedBy: 'directrice'
  }];
  return {
    name: 'full-history',
    title: 'Amine Belkacem',
    doc: TestBed.inject(PdfGeneratorService).buildDocument(studentHistory(), logo, refunds)
  };
}

/** Historique des présences d'une série, par le dialogue lui-même. */
function attendanceHistory(logo: string): PrintFixture {
  const dialog = TestBed.runInInjectionContext(() => new AttendanceHistoryDialogComponent(
    null as never, null as never, null as never, null as never, null as never, translate(), null as never,
    { studentId: 42 }));
  dialog.studentName = 'Amine Belkacem';
  dialog.sessionSeries = [{ id: 12, name: 'Octobre — Maths 4 AM A' } as SessionSeries];
  dialog.selectedGroup = 3;
  dialog.selectedSeries = 12;
  dialog.attendanceHistory.data = Array.from({ length: 12 }, (_, i) => ({
    id: i + 1,
    sessionId: 100 + i,
    isPresent: i % 4 !== 1,
    isJustified: i % 8 === 1,
    isCatchUp: i === 6,
    description: i === 1 ? 'Malade (certificat 05/10/2029)' : '',
    sessionTitle: `Séance ${i + 1} — 4 AM`,
    sessionDate: `2029-10-${String(2 + i * 2).padStart(2, '0')}T14:30:00`
  }));
  return { name: 'attendance-history', title: 'Amine Belkacem', doc: dialog.buildPdfDocument(logo) };
}

/** Historique des paiements d'une série, par le dialogue lui-même. */
function paymentHistory(logo: string): PrintFixture {
  const dialog = TestBed.runInInjectionContext(() => new PaymentHistoryDialogComponent(
    null as never, null as never, { studentId: 42 }, translate()));
  const history = studentHistory();
  dialog.studentName = history.studentName;
  dialog.studentGroups = history.groups;
  dialog.selectedGroup = 3;
  dialog.loadSessionSeries();
  dialog.selectedSeries = 13;
  dialog.loadPaymentHistory();
  return { name: 'payment-history', title: 'Amine Belkacem', doc: dialog.buildPdfDocument(logo) };
}

/** Montant formaté comme les reçus et bordereaux d'un document en arabe, pour les assertions. */
export function arabicAmount(value: number): string {
  return formatAmount(value, 'ar');
}
