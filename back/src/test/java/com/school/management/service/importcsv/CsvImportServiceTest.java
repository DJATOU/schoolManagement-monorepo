package com.school.management.service.importcsv;

import com.school.management.dto.importcsv.ImportResultDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.SubjectEntity;
import com.school.management.persistance.TeacherEntity;
import com.school.management.repository.GroupTypeRepository;
import com.school.management.repository.LevelRepository;
import com.school.management.repository.PricingRepository;
import com.school.management.repository.RoomRepository;
import com.school.management.repository.StudentRepository;
import com.school.management.repository.SubjectRepository;
import com.school.management.repository.TeacherRepository;
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

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
