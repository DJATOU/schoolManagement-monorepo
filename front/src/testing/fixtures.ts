import { Student } from '../app/components/student/domain/student';
import { Group } from '../app/models/group/group';
import { Teacher } from '../app/models/teacher/teacher';
import { StudentPaymentStatus } from '../app/models/student-payment-status';
import { Session } from '../app/models/session/session';

/**
 * Fabriques d'objets de test.
 *
 * Plusieurs composants déclarent une entrée obligatoire (`@Input() student!: Student`) et
 * la déréférencent dès `ngOnInit`. Les specs générées par le CLI ne la fournissaient pas :
 * elles échouaient sur un `undefined`, ce qui ne disait rien du composant.
 *
 * Chaque fabrique renvoie un objet **valide et complet**, surchargeable champ par champ.
 * Le but est qu'une spec n'ait à nommer que ce qui compte pour elle : un test qui recopie
 * quinze champs pour en vérifier un seul rend illisible ce qu'il teste vraiment.
 */

/** Étudiant valide : niveau et tuteur volontairement absents, voir ci-dessous. */
export function aStudent(overrides: Partial<Student> = {}): Student {
  return {
    id: 1,
    firstName: 'Amina',
    lastName: 'Belkacem',
    gender: 'F',
    email: 'amina.belkacem@example.test',
    phoneNumber: '0555123456',
    dateOfBirth: new Date('2008-05-14'),
    placeOfBirth: 'Alger',
    photo: '',
    // `levelId` et `tutorId` sont laissés indéfinis à dessein : renseignés, ils déclenchent
    // un appel HTTP dans `ngOnInit` et le composant ne finit sa construction qu'au retour de
    // la réponse. Une spec qui a besoin de ce chemin les fournit explicitement.
    level: 0,
    levelId: 0,
    levelName: '2AS',
    establishment: 'Lycée El Feth',
    ...overrides
  };
}

/** Groupe valide, avec les libellés que le backend fournit déjà (`GroupMapper`). */
export function aGroup(overrides: Partial<Group> = {}): Group {
  return {
    id: 5,
    name: 'Maths 1B',
    groupTypeId: 2,
    groupTypeName: 'Petit groupe',
    levelId: 3,
    levelName: '2AS',
    subjectId: 4,
    subjectName: 'Mathématiques',
    sessionNumberPerSerie: 4,
    priceId: 7,
    priceAmount: 2000,
    teacherId: 9,
    teacherName: 'Karim Saïdi',
    ...overrides
  };
}

/** Enseignant valide. */
export function aTeacher(overrides: Partial<Teacher> = {}): Teacher {
  return {
    id: 9,
    firstName: 'Karim',
    lastName: 'Saïdi',
    gender: 'M',
    email: 'karim.saidi@example.test',
    phoneNumber: '0555987654',
    dateOfBirth: new Date('1985-02-20'),
    placeOfBirth: 'Oran',
    specialization: 'Mathématiques',
    groups: [],
    ...overrides
  };
}

/** Profil tel que `ProfileCardComponent` et `ProfileListItemComponent` l'attendent. */
export function aProfile(overrides: Record<string, unknown> = {}) {
  return {
    id: '1',
    firstName: 'Amina',
    lastName: 'Belkacem',
    photo: '',
    subtitle: 'Niveau : 2AS',
    email: 'amina.belkacem@example.test',
    phoneNumber: '0555123456',
    ...overrides
  };
}

/** Statut de paiement, par défaut à jour et sans groupe en retard. */
export function aPaymentStatus(
  overrides: Partial<StudentPaymentStatus> = {}
): StudentPaymentStatus {
  return {
    studentId: 1,
    paymentStatus: 'GOOD',
    lateGroups: [],
    totalDue: 0,
    totalPaid: 0,
    ...overrides
  };
}

/** Séance valide, rattachée au groupe de {@link aGroup} et à une série. */
export function aSession(overrides: Partial<Session> = {}): Session {
  return {
    id: 100,
    title: 'Séance 1',
    sessionType: 'COURS',
    sessionTimeStart: new Date('2026-09-07T10:00:00'),
    sessionTimeEnd: new Date('2026-09-07T12:00:00'),
    groupId: 5,
    groupName: 'Maths 1B',
    roomId: 1,
    roomName: 'Salle A',
    sessionSeriesId: 10,
    teacherId: 9,
    teacherName: 'Karim Saïdi',
    active: true,
    students: [],
    ...overrides
  };
}
