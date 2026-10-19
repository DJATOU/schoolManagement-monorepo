package com.school.management.service.importcsv;

import com.school.management.dto.StudentGroupDTO;
import com.school.management.dto.importcsv.ImportResultDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.persistance.SubjectEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.repository.TeacherRepository;
import com.school.management.service.CurrentSchoolYearService;
import com.school.management.service.StudentGroupService;
import com.school.management.service.exception.CustomServiceException;
import com.school.management.service.exception.ReadOnlySchoolYearException;
import com.school.management.service.group.GroupServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockMultipartFile;

import org.springframework.http.HttpStatus;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Import CSV : résolution des référentiels par nom, tarif des groupes, refus des doublons.
 *
 * <p>Ce module n'avait aucun test. C'est pourtant lui qui a cassé en production : réimporter un
 * fichier de niveaux créait des homonymes en silence, et l'import d'élèves échouait ensuite sur
 * « Query did not return a unique result ». Un groupe importé, lui, arrivait sans tarif et ne
 * pouvait encaisser aucun paiement, sans que rien ne le signale.</p>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Import CSV")
class CsvImportServiceTest {

    @Mock private StudentRepository studentRepository;
    @Mock private TeacherRepository teacherRepository;
    @Mock private LevelRepository levelRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private GroupTypeRepository groupTypeRepository;
    @Mock private RoomRepository roomRepository;
    @Mock private PricingRepository pricingRepository;
    @Mock private GroupServiceImpl groupService;
    @Mock private GroupRepository groupRepository;
    @Mock private CurrentSchoolYearService currentSchoolYearService;
    @Mock private StudentGroupService studentGroupService;

    @InjectMocks private CsvImportService service;

    private LevelEntity level;
    private SubjectEntity subject;

    private static MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "import.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    private static PricingEntity pricing(long id, double price) {
        PricingEntity pricing = PricingEntity.builder().build();
        pricing.setId(id);
        pricing.setPrice(price);
        return pricing;
    }

    @BeforeEach
    void referentiels() {
        level = LevelEntity.builder().build();
        level.setName("1er année");
        subject = SubjectEntity.builder().build();
        subject.setName("Math");
        when(levelRepository.findByName("1er année")).thenReturn(Optional.of(level));
        when(subjectRepository.findByNameContaining("Math")).thenReturn(List.of(subject));
    }

    private GroupEntity createdGroup() {
        ArgumentCaptor<GroupEntity> captor = ArgumentCaptor.forClass(GroupEntity.class);
        verify(groupService).createGroup(captor.capture());
        return captor.getValue();
    }

    // ------------------------------------------------------------------
    // Tarif des groupes
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Tarif des groupes")
    class TarifDesGroupes {

        @Test
        @DisplayName("tarif existant : rattaché au groupe, sans avertissement")
        void tarifRattache() {
            PricingEntity tarif = pricing(4L, 2000.0);
            when(pricingRepository.findByPriceOrderByIdAsc(2000.0)).thenReturn(List.of(tarif));

            ImportResultDTO result = service.importGroups(csv(
                    "name,level,subject,price\nMath 1ère A,1er année,Math,2000\n"));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getWarnings()).isEmpty();
            assertThat(createdGroup().getPrice()).isSameAs(tarif);
        }

        @Test
        @DisplayName("virgule décimale acceptée, comme dans l'import des tarifs")
        void virguleDecimale() {
            // Un tableur réglé en français exporte « 2500,50 ». Le refuser ferait échouer des
            // fichiers que l'import des tarifs, lui, accepte déjà.
            PricingEntity tarif = pricing(5L, 2500.5);
            when(pricingRepository.findByPriceOrderByIdAsc(2500.5)).thenReturn(List.of(tarif));

            ImportResultDTO result = service.importGroups(csv(
                    "name;level;subject;price\nMath 1ère A;1er année;Math;2500,50\n"));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(createdGroup().getPrice()).isSameAs(tarif);
        }

        @Test
        @DisplayName("plusieurs tarifs du même montant : le plus ancien, pour un résultat reproductible")
        void plusieursTarifsIdentiques() {
            PricingEntity ancien = pricing(2L, 2000.0);
            PricingEntity recent = pricing(9L, 2000.0);
            when(pricingRepository.findByPriceOrderByIdAsc(2000.0)).thenReturn(List.of(ancien, recent));

            service.importGroups(csv("name,level,subject,price\nG,1er année,Math,2000\n"));

            assertThat(createdGroup().getPrice()).isSameAs(ancien);
        }

        @Test
        @DisplayName("sans colonne price : groupe créé, mais signalé comme ne pouvant rien encaisser")
        void sansTarif() {
            // Les fichiers existants n'ont pas de colonne price : ils doivent continuer à passer.
            // Mais un groupe sans tarif ne peut recevoir aucun paiement, et le découvrir devant un
            // parent qui paie est trop tard.
            ImportResultDTO result = service.importGroups(csv(
                    "name,level,subject\nMath 1ère A,1er année,Math\n"));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).as("ce n'est pas un échec : le groupe est créé").isEmpty();
            assertThat(result.getWarnings()).singleElement().satisfies(warning -> {
                assertThat(warning.getLine()).isEqualTo(2);
                assertThat(warning.getMessage())
                        .contains("Math 1ère A")
                        .contains("sans tarif");
            });
            assertThat(createdGroup().getPrice()).isNull();
        }

        @Test
        @DisplayName("colonne price vide sur une ligne : même traitement qu'une colonne absente")
        void celluleVide() {
            ImportResultDTO result = service.importGroups(csv(
                    "name,level,subject,price\nMath 1ère A,1er année,Math,\n"));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getWarnings()).hasSize(1);
        }

        @Test
        @DisplayName("tarif inconnu : ligne rejetée, et le message dit d'importer le tarif d'abord")
        void tarifInconnu() {
            // Un tarif est désigné, jamais créé par l'import de groupes : un tarif fabriqué au fil
            // de l'eau échapperait à l'administrateur qui gère la grille tarifaire.
            when(pricingRepository.findByPriceOrderByIdAsc(3000.0)).thenReturn(List.of());

            ImportResultDTO result = service.importGroups(csv(
                    "name,level,subject,price\nMath 1ère A,1er année,Math,3000\n"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement().satisfies(error ->
                    assertThat(error.getMessage())
                            .contains("Tarif introuvable : 3000")
                            .contains("Importez d'abord ce tarif"));
            verify(groupService, never()).createGroup(any());
            verify(pricingRepository, never()).save(any());
        }

        @Test
        @DisplayName("prix non numérique : ligne rejetée, rien n'est créé")
        void prixInvalide() {
            ImportResultDTO result = service.importGroups(csv(
                    "name,level,subject,price\nMath 1ère A,1er année,Math,deux mille\n"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement()
                    .extracting(ImportResultDTO.ImportError::getMessage)
                    .asString().contains("Prix invalide");
            verify(groupService, never()).createGroup(any());
        }

        @Test
        @DisplayName("une ligne en échec n'empêche pas les suivantes")
        void importPartiel() {
            PricingEntity tarif = pricing(4L, 2000.0);
            when(pricingRepository.findByPriceOrderByIdAsc(2000.0)).thenReturn(List.of(tarif));
            when(pricingRepository.findByPriceOrderByIdAsc(9999.0)).thenReturn(List.of());

            ImportResultDTO result = service.importGroups(csv("""
                    name,level,subject,price
                    G1,1er année,Math,2000
                    G2,1er année,Math,9999
                    G3,1er année,Math,2000
                    """));

            assertThat(result.getImported()).isEqualTo(2);
            assertThat(result.getErrors()).singleElement()
                    .extracting(ImportResultDTO.ImportError::getLine).isEqualTo(3);
        }
    }

    // ------------------------------------------------------------------
    // Doublons de référentiels (correction e5412cc)
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Doublons de référentiels")
    class Doublons {

        @Test
        @DisplayName("niveau déjà présent : ligne refusée et nommée, aucun homonyme créé")
        void niveauDejaPresent() {
            when(levelRepository.existsByName("1er année")).thenReturn(true);
            when(levelRepository.existsByName("2eme année")).thenReturn(false);

            ImportResultDTO result = service.importLevels(csv("""
                    name,levelCode,levelSequence
                    1er année,N1,1
                    2eme année,N2,2
                    """));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(2);
                assertThat(error.getMessage()).contains("Niveau déjà présent : 1er année");
            });
            ArgumentCaptor<LevelEntity> captor = ArgumentCaptor.forClass(LevelEntity.class);
            verify(levelRepository).save(captor.capture());
            assertThat(captor.getValue().getName()).isEqualTo("2eme année");
        }

        @Test
        @DisplayName("matière, salle, type de groupe : même refus")
        void autresReferentiels() {
            when(subjectRepository.existsByName("Math")).thenReturn(true);
            when(roomRepository.existsByName("Salle 1")).thenReturn(true);
            when(groupTypeRepository.existsByName("Petit")).thenReturn(true);

            assertThat(service.importSubjects(csv("name\nMath\n")).getErrors())
                    .singleElement().extracting(ImportResultDTO.ImportError::getMessage)
                    .asString().contains("Matière déjà présente");
            assertThat(service.importRooms(csv("name\nSalle 1\n")).getErrors())
                    .singleElement().extracting(ImportResultDTO.ImportError::getMessage)
                    .asString().contains("Salle déjà présente");
            assertThat(service.importGroupTypes(csv("name\nPetit\n")).getErrors())
                    .singleElement().extracting(ImportResultDTO.ImportError::getMessage)
                    .asString().contains("Type de groupe déjà présent");

            verify(subjectRepository, never()).save(any());
            verify(roomRepository, never()).save(any());
            verify(groupTypeRepository, never()).save(any());
        }

        @Test
        @DisplayName("enseignant déjà présent : ligne refusée et nommée ; un nouvel enseignant passe")
        void enseignantDejaPresent() {
            when(teacherRepository.existsByFullName("Yasmine", "Belaïd")).thenReturn(true);
            when(teacherRepository.existsByFullName("Karim", "Haddad")).thenReturn(false);

            ImportResultDTO result = service.importTeachers(csv("""
                    firstName,lastName,specialization
                     Yasmine , Belaïd ,Anglais
                    Karim,Haddad,SVT
                    """));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(2);
                assertThat(error.getMessage()).isEqualTo(
                        "Enseignant déjà présent : Yasmine Belaïd. Ligne ignorée pour ne pas créer de doublon.");
            });
            ArgumentCaptor<TeacherEntity> captor = ArgumentCaptor.forClass(TeacherEntity.class);
            verify(teacherRepository).save(captor.capture());
            assertThat(captor.getValue().getLastName()).isEqualTo("Haddad");
        }

        @Test
        @DisplayName("enseignant répété dans le fichier, casse comprise : créé une fois, la répétition nommée")
        void enseignantRepeteDansLeFichier() {
            when(teacherRepository.existsByFullName(any(), any())).thenReturn(false);

            ImportResultDTO result = service.importTeachers(csv("""
                    firstName,lastName
                    Yasmine,Belaïd
                    Karim,Haddad
                    yasmine,BELAÏD
                    """));

            assertThat(result.getImported()).isEqualTo(2);
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(4);
                assertThat(error.getMessage()).isEqualTo(
                        "Enseignant en double dans le fichier : yasmine BELAÏD (déjà ligne 2). Ligne ignorée.");
            });
            verify(teacherRepository, org.mockito.Mockito.times(2)).save(any());
        }
    }

    // ------------------------------------------------------------------
    // Inscriptions (élève → groupe)
    // ------------------------------------------------------------------

    private static final String ENROLMENT_HEADER = "firstName,lastName,group,arrivalDate,phoneNumber\n";

    private static StudentEntity student(long id, String firstName, String lastName, String phone) {
        StudentEntity student = StudentEntity.builder().status(StudentStatus.ACTIVE).build();
        student.setId(id);
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setPhoneNumber(phone);
        return student;
    }

    private static GroupEntity group(long id, String name) {
        GroupEntity group = GroupEntity.builder().name(name).build();
        group.setId(id);
        return group;
    }

    private static String messageOf(ImportResultDTO result) {
        assertThat(result.getErrors()).hasSize(1);
        return result.getErrors().get(0).getMessage();
    }

    /**
     * L'import des inscriptions résout l'élève et le groupe, puis délègue TOUS les contrôles métier
     * à « Ajouter un étudiant » ({@link StudentGroupService#addStudentsToGroup}), simulé ici : ses
     * refus sont ceux du vrai service, prouvés de bout en bout par
     * {@code EnrolmentCsvImportIntegrationTest}.
     */
    @Nested
    @DisplayName("Inscriptions")
    class Inscriptions {

        private static final long YEAR_ID = 3L;

        private StudentEntity lea;
        private GroupEntity physique;
        private GroupEntity mathsA;

        @BeforeEach
        void anneeCouranteEtReferentiels() {
            SchoolYearEntity currentYear = SchoolYearEntity.builder().label("2026-2027").isCurrent(true).build();
            currentYear.setId(YEAR_ID);
            when(currentSchoolYearService.findCurrent()).thenReturn(Optional.of(currentYear));

            lea = student(11L, "Léa", "Haddad", "0550000102");
            physique = group(21L, "Physique 4 AM");
            mathsA = group(22L, "Maths 4 AM A");
            when(studentRepository.findByFullNameAndStatus("Léa", "Haddad", StudentStatus.ACTIVE))
                    .thenReturn(List.of(lea));
            when(groupRepository.findActiveBySchoolYearIdAndName(YEAR_ID, "Physique 4 AM"))
                    .thenReturn(List.of(physique));
            when(groupRepository.findActiveBySchoolYearIdAndName(YEAR_ID, "Maths 4 AM A"))
                    .thenReturn(List.of(mathsA));
            when(studentGroupService.addStudentsToGroup(any())).thenReturn(1);
        }

        private StudentGroupDTO sentToService() {
            ArgumentCaptor<StudentGroupDTO> captor = ArgumentCaptor.forClass(StudentGroupDTO.class);
            verify(studentGroupService).addStudentsToGroup(captor.capture());
            return captor.getValue();
        }

        @Test
        @DisplayName("ligne valide : inscrite par « Ajouter un étudiant », à la date d'arrivée du fichier")
        void ligneValide() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getWarnings()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            StudentGroupDTO dto = sentToService();
            assertThat(dto.getGroupId()).isEqualTo(21L);
            assertThat(dto.getStudentIds()).containsExactly(11L);
            assertThat(dto.getDateAssigned()).isEqualTo(LocalDate.of(2026, 9, 15));
        }

        @Test
        @DisplayName("date future admise : transmise telle quelle (inscription d'avance)")
        void dateFuture() {
            service.importEnrolments(csv(ENROLMENT_HEADER + "Léa,Haddad,Physique 4 AM,2027-05-03,\n"));

            assertThat(sentToService().getDateAssigned()).isEqualTo(LocalDate.of(2027, 5, 3));
        }

        @Test
        @DisplayName("en-têtes en casse quelconque, point-virgule, sans colonne phoneNumber : reconnus")
        void enTetesInsensiblesALaCasse() {
            ImportResultDTO result = service.importEnrolments(csv(
                    "FIRSTNAME;LastName;Group;ARRIVALDATE\n Léa ; Haddad ;Physique 4 AM; 2026-09-15 \n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            assertThat(sentToService().getStudentIds()).containsExactly(11L);
        }

        @Test
        @DisplayName("date d'arrivée absente : ligne refusée, aucune date par défaut")
        void dateAbsente() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,,\n"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(2);
                assertThat(error.getMessage())
                        .isEqualTo("Obligatoire(s) manquant(s) : arrivalDate. La date d'arrivée n'a pas de "
                                + "valeur par défaut : c'est elle qui décide des séances dues.");
            });
            // Appelée sans date, la méthode métier prendrait le jour même.
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("colonne arrivalDate absente de l'en-tête : même refus")
        void colonneDateAbsente() {
            ImportResultDTO result = service.importEnrolments(csv(
                    "firstName,lastName,group\nLéa,Haddad,Physique 4 AM\n"));

            assertThat(messageOf(result)).startsWith("Obligatoire(s) manquant(s) : arrivalDate.");
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("prénom, nom ou groupe absents : refusés, colonnes nommées")
        void autresColonnesObligatoires() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER + """
                    ,Haddad,Physique 4 AM,2026-09-15,
                    Léa,,Physique 4 AM,2026-09-15,
                    Léa,Haddad,,2026-09-15,
                    ,,,,
                    """));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).extracting(ImportResultDTO.ImportError::getLine)
                    .containsExactly(2, 3, 4, 5);
            assertThat(result.getErrors()).extracting(ImportResultDTO.ImportError::getMessage).containsExactly(
                    "Obligatoire(s) manquant(s) : firstName.",
                    "Obligatoire(s) manquant(s) : lastName.",
                    "Obligatoire(s) manquant(s) : group.",
                    "Obligatoire(s) manquant(s) : firstName, lastName, group, arrivalDate. La date d'arrivée "
                            + "n'a pas de valeur par défaut : c'est elle qui décide des séances dues.");
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("date invalide (format français, jour inexistant) : refusée avec le format attendu")
        void dateInvalide() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER + """
                    Léa,Haddad,Physique 4 AM,15/09/2026,
                    Léa,Haddad,Physique 4 AM,2026-02-30,
                    Léa,Haddad,Physique 4 AM,2026-9-15,
                    """));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).extracting(ImportResultDTO.ImportError::getMessage).containsExactly(
                    "Date d'arrivée invalide : 15/09/2026 (format attendu AAAA-MM-JJ, par exemple 2026-09-15).",
                    "Date d'arrivée invalide : 2026-02-30 (format attendu AAAA-MM-JJ, par exemple 2026-09-15).",
                    "Date d'arrivée invalide : 2026-9-15 (format attendu AAAA-MM-JJ, par exemple 2026-09-15).");
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("élève inconnu : refusé, l'élève nommé")
        void eleveInconnu() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Inconnu,Personne,Physique 4 AM,2026-09-15,\n"));

            assertThat(messageOf(result)).isEqualTo(
                    "Élève introuvable parmi les élèves actifs : Inconnu Personne. Importez d'abord les élèves.");
            verifyNoInteractions(studentGroupService);
        }

        @Nested
        @DisplayName("Homonymes")
        class Homonymes {

            private StudentEntity yanis1;
            private StudentEntity yanis2;

            @BeforeEach
            void deuxYanisSaidi() {
                yanis1 = student(31L, "Yanis", "Saidi", "0550000201");
                yanis2 = student(32L, "Yanis", "Saidi", "0550 00 02 02");
                when(studentRepository.findByFullNameAndStatus("Yanis", "Saidi", StudentStatus.ACTIVE))
                        .thenReturn(List.of(yanis1, yanis2));
            }

            @Test
            @DisplayName("sans téléphone : refusé, le message demande la colonne phoneNumber")
            void sansTelephone() {
                ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                        + "Yanis,Saidi,Physique 4 AM,2026-09-15,\n"));

                assertThat(messageOf(result)).isEqualTo("Plusieurs élèves actifs s'appellent Yanis Saidi (2) : "
                        + "renseignez la colonne phoneNumber pour désigner le bon.");
                verifyNoInteractions(studentGroupService);
            }

            @Test
            @DisplayName("avec téléphone : le bon élève, espaces et ponctuation ignorés")
            void avecTelephone() {
                ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                        + "Yanis,Saidi,Physique 4 AM,2026-09-15,055-000-0202\n"));

                assertThat(result.getErrors()).isEmpty();
                assertThat(result.getWarnings()).isEmpty();
                assertThat(result.getImported()).isEqualTo(1);
                assertThat(sentToService().getStudentIds()).containsExactly(32L);
            }

            @Test
            @DisplayName("téléphone d'aucun des homonymes : refusé, le téléphone nommé")
            void telephoneInconnu() {
                ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                        + "Yanis,Saidi,Physique 4 AM,2026-09-15,0699999999\n"));

                assertThat(messageOf(result)).isEqualTo(
                        "Plusieurs élèves actifs s'appellent Yanis Saidi, mais aucun n'a le téléphone 0699999999.");
                verifyNoInteractions(studentGroupService);
            }

            @Test
            @DisplayName("homonymes au même téléphone : refusé, renvoi vers « Ajouter un étudiant »")
            void memeTelephone() {
                yanis2.setPhoneNumber("0550000201");

                ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                        + "Yanis,Saidi,Physique 4 AM,2026-09-15,0550000201\n"));

                assertThat(messageOf(result)).contains("et ont le téléphone 0550000201")
                        .contains("Ajouter un étudiant");
                verifyNoInteractions(studentGroupService);
            }
        }

        @Test
        @DisplayName("nom unique et téléphone différent : inscrit, mais signalé en avertissement sur la ligne")
        void nomUniqueTelephoneDifferent() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,0699999999\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            assertThat(sentToService().getStudentIds()).containsExactly(11L);
            assertThat(result.getWarnings()).singleElement().satisfies(warning -> {
                assertThat(warning.getLine()).isEqualTo(2);
                assertThat(warning.getMessage()).isEqualTo("Téléphone différent : Léa Haddad est inscrit, mais "
                        + "le téléphone du fichier (0699999999) ne correspond pas à celui de sa fiche "
                        + "(0550000102). Vérifiez qu'il s'agit du bon élève.");
            });
        }

        @Test
        @DisplayName("nom unique sans téléphone enregistré : inscrit, avertissement « téléphone non vérifié »")
        void nomUniqueSansTelephoneEnregistre() {
            lea.setPhoneNumber(null);

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,0699999999\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getWarnings()).singleElement().satisfies(warning -> {
                assertThat(warning.getLine()).isEqualTo(2);
                assertThat(warning.getMessage()).isEqualTo("Téléphone non vérifié : Léa Haddad est inscrit, "
                        + "mais sa fiche n'a aucun téléphone à comparer à celui du fichier (0699999999). "
                        + "Vérifiez qu'il s'agit du bon élève.");
            });
        }

        @Test
        @DisplayName("nom unique, même téléphone écrit autrement : inscrit sans avertissement")
        void nomUniqueMemeTelephoneAutreFormat() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,0550 00.01-02\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getWarnings()).isEmpty();
        }

        @Test
        @DisplayName("téléphone différent sur une ligne refusée : l'erreur seule, sans avertissement")
        void telephoneDifferentLigneRefusee() {
            when(studentGroupService.addStudentsToGroup(any())).thenReturn(0);

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,0699999999\n"));

            assertThat(result.getImported()).isZero();
            assertThat(messageOf(result)).startsWith("Déjà inscrit");
            assertThat(result.getWarnings()).isEmpty();
        }

        @Test
        @DisplayName("groupe inconnu : refusé, groupe et année courante nommés")
        void groupeInconnu() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Chimie 4 AM,2026-09-15,\n"));

            assertThat(messageOf(result)).isEqualTo("Groupe introuvable dans l'année scolaire courante (2026-2027) : "
                    + "Chimie 4 AM. Importez d'abord les groupes ; un groupe d'une année close ne reçoit plus "
                    + "d'inscription.");
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("groupe d'une année passée : cherché dans l'année courante seulement, donc refusé")
        void groupeDUneAnneePassee() {
            // « Anglais 4 AM » n'existe que dans une année close : la recherche, bornée à l'année
            // courante, ne le trouve pas.
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Anglais 4 AM,2026-09-15,\n"));

            assertThat(messageOf(result)).contains("Anglais 4 AM").contains("(2026-2027)")
                    .contains("un groupe d'une année close ne reçoit plus d'inscription");
            verify(groupRepository).findActiveBySchoolYearIdAndName(YEAR_ID, "Anglais 4 AM");
            verify(groupRepository, never()).findActiveBySchoolYearIdAndName(
                    argThat(id -> id != YEAR_ID), anyString());
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("filet de sécurité : année close refusée par le service (409), refus rapporté sur la ligne")
        void anneeCloseRefuseeParLeService() {
            when(studentGroupService.addStudentsToGroup(any())).thenThrow(new ReadOnlySchoolYearException());

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(result.getImported()).isZero();
            assertThat(messageOf(result)).isEqualTo("Inscription refusée : " + ReadOnlySchoolYearException.DEFAULT_MESSAGE);
        }

        @Test
        @DisplayName("deux groupes du même nom dans l'année : refusé, aucun choisi au hasard")
        void groupesHomonymes() {
            when(groupRepository.findActiveBySchoolYearIdAndName(YEAR_ID, "Physique 4 AM"))
                    .thenReturn(List.of(physique, group(29L, "Physique 4 AM")));

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(messageOf(result)).isEqualTo("Plusieurs groupes « Physique 4 AM » dans l'année scolaire "
                    + "courante (2026-2027) : renommez-les pour les distinguer.");
            verifyNoInteractions(studentGroupService);
        }

        @Test
        @DisplayName("aucune année courante : erreur en ligne 0, aucune ligne traitée")
        void aucuneAnneeCourante() {
            when(currentSchoolYearService.findCurrent()).thenReturn(Optional.empty());

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isZero();
                assertThat(error.getMessage()).startsWith("Aucune année scolaire courante définie");
            });
            verifyNoInteractions(studentGroupService);
            verify(groupRepository, never()).findActiveBySchoolYearIdAndName(anyLong(), anyString());
        }

        @Test
        @DisplayName("niveau différent : refus de la règle existante, message du serveur repris")
        void niveauDifferent() {
            String serverMessage = "L'étudiant Léa Haddad (niveau 4 AM) ne peut pas être inscrit au groupe "
                    + "« Physique 4 AM » de niveau 1 AS. Un étudiant ne s'inscrit que dans des groupes de son niveau.";
            when(studentGroupService.addStudentsToGroup(any()))
                    .thenThrow(new CustomServiceException(serverMessage, HttpStatus.BAD_REQUEST));

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(2);
                assertThat(error.getMessage()).isEqualTo("Inscription refusée : " + serverMessage);
            });
        }

        @Test
        @DisplayName("déjà inscrit (aucune inscription créée) : erreur de ligne, pas de doublon")
        void dejaInscrit() {
            when(studentGroupService.addStudentsToGroup(any())).thenReturn(0);

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER
                    + "Léa,Haddad,Physique 4 AM,2026-09-15,\n"));

            assertThat(result.getImported()).isZero();
            assertThat(messageOf(result)).isEqualTo("Déjà inscrit : Léa Haddad suit déjà le groupe « Physique 4 AM » "
                    + "(inscription en cours). Ligne ignorée pour ne pas créer de doublon.");
        }

        @Test
        @DisplayName("un élève dans deux groupes : deux lignes, deux inscriptions")
        void unEleveDansDeuxGroupes() {
            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER + """
                    Léa,Haddad,Physique 4 AM,2026-09-15,
                    Léa,Haddad,Maths 4 AM A,2026-10-05,
                    """));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(2);
            ArgumentCaptor<StudentGroupDTO> captor = ArgumentCaptor.forClass(StudentGroupDTO.class);
            verify(studentGroupService, times(2)).addStudentsToGroup(captor.capture());
            assertThat(captor.getAllValues()).extracting(StudentGroupDTO::getGroupId).containsExactly(21L, 22L);
            assertThat(captor.getAllValues()).extracting(StudentGroupDTO::getDateAssigned)
                    .containsExactly(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 10, 5));
            assertThat(captor.getAllValues()).allSatisfy(dto ->
                    assertThat(dto.getStudentIds()).containsExactly(11L));
        }

        @Test
        @DisplayName("fichier mixte : lignes valides inscrites, lignes invalides rapportées avec leur numéro")
        void fichierMixte() {
            when(studentGroupService.addStudentsToGroup(argThat(dto -> dto != null
                    && Long.valueOf(22L).equals(dto.getGroupId()))))
                    .thenThrow(new CustomServiceException("refus niveau", HttpStatus.BAD_REQUEST));

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER + """
                    Léa,Haddad,Physique 4 AM,2026-09-15,
                    Léa,Haddad,Maths 4 AM A,2026-09-15,
                    Inconnu,Personne,Physique 4 AM,2026-09-15,
                    Léa,Haddad,Physique 4 AM,15/09/2026,
                    Léa,Haddad,Physique 4 AM,2026-09-16,
                    """));

            // Lignes 3 (refus serveur), 4 (élève inconnu), 5 (date) ; la ligne 6, après elles, est traitée.
            assertThat(result.getImported()).isEqualTo(2);
            assertThat(result.getErrors()).extracting(ImportResultDTO.ImportError::getLine)
                    .containsExactly(3, 4, 5);
            assertThat(result.getErrors()).extracting(ImportResultDTO.ImportError::getMessage)
                    .satisfiesExactly(
                            m -> assertThat(m).isEqualTo("Inscription refusée : refus niveau"),
                            m -> assertThat(m).startsWith("Élève introuvable"),
                            m -> assertThat(m).startsWith("Date d'arrivée invalide"));
            verify(studentGroupService, times(3)).addStudentsToGroup(any());
            verify(studentGroupService).addStudentsToGroup(argThat(dto -> dto != null
                    && LocalDate.of(2026, 9, 16).equals(dto.getDateAssigned())));
        }

        @Test
        @DisplayName("erreur inattendue sur une ligne : rapportée, les suivantes traitées")
        void erreurInattendue() {
            when(studentGroupService.addStudentsToGroup(argThat(dto -> dto != null
                    && Long.valueOf(21L).equals(dto.getGroupId()))))
                    .thenThrow(new IllegalStateException("panne"));

            ImportResultDTO result = service.importEnrolments(csv(ENROLMENT_HEADER + """
                    Léa,Haddad,Physique 4 AM,2026-09-15,
                    Léa,Haddad,Maths 4 AM A,2026-09-15,
                    """));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isEqualTo(2);
                assertThat(error.getMessage()).isEqualTo("panne");
            });
            verify(studentGroupService, times(2)).addStudentsToGroup(any());
        }
    }

    // ------------------------------------------------------------------
    // Fichier
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Fichier")
    class Fichier {

        @Test
        @DisplayName("fichier vide : erreur en ligne 0, aucune écriture")
        void fichierVide() {
            ImportResultDTO result = service.importGroups(csv(""));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement()
                    .extracting(ImportResultDTO.ImportError::getLine).isEqualTo(0);
            verify(groupService, never()).createGroup(any());
        }

        @Test
        @DisplayName("BOM UTF-8 en tête : l'en-tête reste lisible")
        void bomUtf8() {
            PricingEntity tarif = pricing(4L, 2000.0);
            when(pricingRepository.findByPriceOrderByIdAsc(2000.0)).thenReturn(List.of(tarif));

            ImportResultDTO result = service.importGroups(csv(
                    "\uFEFFname,level,subject,price\nG,1er année,Math,2000\n"));

            assertThat(result.getImported()).isEqualTo(1);
            assertThat(result.getErrors()).isEmpty();
        }

        @Test
        @DisplayName("BOM UTF-8 devant « firstName » : la colonne est reconnue, accents compris")
        void bomDevantLePrenom() {
            // Le défaut à éviter : « \uFEFFfirstName » n'est plus la colonne firstName, et chaque
            // élève est refusé (« Prénom et nom obligatoires. »).
            when(levelRepository.findByName("1er année")).thenReturn(Optional.of(level));

            ImportResultDTO result = service.importStudents(csv(
                    "\uFEFFfirstName,lastName,gender,level,establishment\nLéa,Haddad,F,1er année,Lycée Émir Abdelkader\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            ArgumentCaptor<com.school.management.persistance.StudentEntity> saved =
                    ArgumentCaptor.forClass(com.school.management.persistance.StudentEntity.class);
            verify(studentRepository).save(saved.capture());
            assertThat(saved.getValue().getFirstName()).isEqualTo("Léa");
            assertThat(saved.getValue().getEstablishment()).isEqualTo("Lycée Émir Abdelkader");
            assertThat(saved.getValue().getLevel()).isSameAs(level);
        }

        @Test
        @DisplayName("BOM et point-virgule (Excel réglé en français, « CSV UTF-8 ») : importé")
        void bomEtPointVirgule() {
            ImportResultDTO result = service.importTeachers(csv(
                    "\uFEFFfirstName;lastName;specialization\r\nCéline;Mahiout;Français\r\n"));

            assertThat(result.getErrors()).isEmpty();
            assertThat(result.getImported()).isEqualTo(1);
            ArgumentCaptor<TeacherEntity> saved = ArgumentCaptor.forClass(TeacherEntity.class);
            verify(teacherRepository).save(saved.capture());
            assertThat(saved.getValue().getFirstName()).isEqualTo("Céline");
            assertThat(saved.getValue().getSpecialization()).isEqualTo("Français");
        }

        @Test
        @DisplayName("fichier réduit à un BOM (feuille vide enregistrée par Excel) : en-tête manquant")
        void bomSeul() {
            // Trois octets, aucun texte : sans retrait du BOM avant le contrôle, l'en-tête passait
            // pour non vide et l'import annonçait « 0 importé, 0 erreur », sans rien expliquer.
            ImportResultDTO result = service.importStudents(csv("\uFEFF"));

            assertThat(result.getImported()).isZero();
            assertThat(result.getErrors()).singleElement().satisfies(error -> {
                assertThat(error.getLine()).isZero();
                assertThat(error.getMessage()).isEqualTo("En-tête CSV manquant.");
            });
            verify(studentRepository, never()).save(any());
        }
    }
}
