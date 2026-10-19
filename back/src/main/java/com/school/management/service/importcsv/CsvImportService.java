package com.school.management.service.importcsv;

import com.school.management.dto.StudentGroupDTO;
import com.school.management.dto.importcsv.ImportResultDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.RoomEntity;
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
import com.school.management.service.group.GroupServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Service d'import CSV minimal (élèves, enseignants, groupes).
 *
 * <p>Chaque import lit un fichier CSV avec une ligne d'en-tête (noms de colonnes), crée les
 * lignes valides et poursuit malgré les erreurs ponctuelles : un résumé
 * ({@link ImportResultDTO}) indique le nombre d'éléments importés et la liste des erreurs par
 * ligne. Le séparateur accepté est la virgule {@code ,} ou le point-virgule {@code ;}.</p>
 *
 * <p>Les entités référencées par nom (niveau, matière, type de groupe, enseignant) doivent déjà
 * exister : elles sont résolues, jamais créées ici (import volontairement minimal).</p>
 *
 * <p><b>Doublons des référentiels.</b> Les imports de niveaux, matières, salles et types de
 * groupe refusent un nom déjà présent et le signalent ligne à ligne, au lieu de créer un
 * homonyme. Sans cette garde, réimporter un même fichier créait silencieusement des doublons ;
 * l'échec ne se manifestait qu'ensuite, dans l'import d'élèves ou de groupes, où la résolution
 * par nom devient indécidable et remonte un « Query did not return a unique result » qui ne
 * nomme ni le doublon ni l'entité concernée. Le refus est donc posé là où la cause est
 * nommable. L'unicité est également portée par le stockage (index uniques, migration V4) :
 * elle vaut alors quel que soit le nombre d'instances de l'application, et deux imports
 * concurrents ne peuvent pas contourner la garde applicative.</p>
 *
 * <p><b>Doublons d'enseignants.</b> Même refus pour un enseignant déjà présent (prénom et nom,
 * casse et espaces de bord ignorés), ou répété dans le fichier. Le fichier des enseignants importé
 * deux fois avait créé une seconde fiche par personne, comptée par le tableau de bord. Pas d'index
 * unique ici : deux enseignants homonymes restent possibles par la saisie manuelle. Les élèves ne
 * sont pas concernés, deux élèves pouvant légitimement porter le même nom.</p>
 *
 * <p><b>Inscriptions.</b> L'import des inscriptions (élève → groupe, à une date d'arrivée) passe
 * par le chemin de « Ajouter un étudiant » ({@link StudentGroupService#addStudentsToGroup}), qui
 * porte tous les contrôles métier. C'est le seul import sans transaction englobante : chaque ligne
 * est validée dans sa propre transaction, pour qu'un refus n'annule que sa ligne (voir
 * {@link #importEnrolments(MultipartFile)}).</p>
 */
@Service
public class CsvImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvImportService.class);

    /**
     * Format de la date d'arrivée : {@code yyyy-MM-dd}, celui de l'API ({@code StudentGroupDTO})
     * et de l'écran. Résolution stricte : un jour inexistant (2026-02-30) est refusé, pas corrigé.
     */
    private static final DateTimeFormatter ARRIVAL_DATE_FORMAT =
            DateTimeFormatter.ofPattern("uuuu-MM-dd").withResolverStyle(ResolverStyle.STRICT);

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final LevelRepository levelRepository;
    private final SubjectRepository subjectRepository;
    private final GroupTypeRepository groupTypeRepository;
    private final RoomRepository roomRepository;
    private final PricingRepository pricingRepository;
    private final GroupServiceImpl groupService;
    private final GroupRepository groupRepository;
    private final CurrentSchoolYearService currentSchoolYearService;
    /** Seul chemin d'écriture d'une inscription : aucun accès direct à la table d'inscription ici. */
    private final StudentGroupService studentGroupService;

    @Autowired
    public CsvImportService(StudentRepository studentRepository,
                            TeacherRepository teacherRepository,
                            LevelRepository levelRepository,
                            SubjectRepository subjectRepository,
                            GroupTypeRepository groupTypeRepository,
                            RoomRepository roomRepository,
                            PricingRepository pricingRepository,
                            GroupServiceImpl groupService,
                            GroupRepository groupRepository,
                            CurrentSchoolYearService currentSchoolYearService,
                            StudentGroupService studentGroupService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.levelRepository = levelRepository;
        this.subjectRepository = subjectRepository;
        this.groupTypeRepository = groupTypeRepository;
        this.roomRepository = roomRepository;
        this.pricingRepository = pricingRepository;
        this.groupService = groupService;
        this.groupRepository = groupRepository;
        this.currentSchoolYearService = currentSchoolYearService;
        this.studentGroupService = studentGroupService;
    }

    /**
     * Importe des tarifs (prix par séance) depuis un CSV.
     * Colonnes reconnues : {@code price}. Obligatoire : {@code price} (nombre décimal).
     * Le tarif est une entité autonome (montant) ; il est ensuite rattaché à un groupe via le
     * formulaire de groupe.
     */
    @Transactional
    public ImportResultDTO importPricing(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String priceRaw = value(row, col, "price");
                if (isBlank(priceRaw)) {
                    result.addError(lineNumber, "Prix obligatoire.");
                    continue;
                }
                double price;
                try {
                    price = Double.parseDouble(priceRaw.trim().replace(",", "."));
                } catch (NumberFormatException nfe) {
                    result.addError(lineNumber, "Prix invalide : " + priceRaw);
                    continue;
                }
                PricingEntity pricing = PricingEntity.builder().build();
                pricing.setPrice(price);
                pricing.setActive(true);
                pricingRepository.save(pricing);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import tarif ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des niveaux depuis un CSV.
     * Colonnes reconnues : {@code name,levelCode,levelSequence}. Obligatoire : {@code name}.
     * Le {@code levelSequence} (rang de passage) est fortement recommandé pour l'assistant de
     * fin d'année.
     */
    @Transactional
    public ImportResultDTO importLevels(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String name = value(row, col, "name");
                if (isBlank(name)) {
                    result.addError(lineNumber, "Nom du niveau obligatoire.");
                    continue;
                }
                // Un niveau est désigné par son nom dans les imports d'élèves et de groupes.
                // Créer un homonyme rendrait ces imports impossibles avec une erreur qui ne
                // mentionne ni le doublon ni le niveau ; on refuse donc la ligne ici, là où
                // la cause est nommable.
                if (levelRepository.existsByName(name.trim())) {
                    result.addError(lineNumber, "Niveau déjà présent : " + name.trim()
                            + ". Ligne ignorée pour ne pas créer de doublon.");
                    continue;
                }
                LevelEntity level = LevelEntity.builder().build();
                level.setName(name.trim());
                level.setLevelCode(emptyToNull(value(row, col, "levelcode")));
                level.setActive(true);

                String seq = value(row, col, "levelsequence");
                if (!isBlank(seq)) {
                    try {
                        level.setLevelSequence(Integer.parseInt(seq.trim()));
                    } catch (NumberFormatException nfe) {
                        result.addError(lineNumber, "Rang (levelSequence) invalide : " + seq);
                        continue;
                    }
                }
                levelRepository.save(level);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import niveau ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des matières depuis un CSV.
     * Colonnes reconnues : {@code name}. Obligatoire : {@code name}.
     */
    @Transactional
    public ImportResultDTO importSubjects(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String name = value(row, col, "name");
                if (isBlank(name)) {
                    result.addError(lineNumber, "Nom de la matière obligatoire.");
                    continue;
                }
                if (subjectRepository.existsByName(name.trim())) {
                    result.addError(lineNumber, "Matière déjà présente : " + name.trim()
                            + ". Ligne ignorée pour ne pas créer de doublon.");
                    continue;
                }
                SubjectEntity subject = SubjectEntity.builder().build();
                subject.setName(name.trim());
                subject.setActive(true);
                subjectRepository.save(subject);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import matière ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des salles depuis un CSV.
     * Colonnes reconnues : {@code name,capacity}. Obligatoire : {@code name}.
     */
    @Transactional
    public ImportResultDTO importRooms(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String name = value(row, col, "name");
                if (isBlank(name)) {
                    result.addError(lineNumber, "Nom de la salle obligatoire.");
                    continue;
                }
                if (roomRepository.existsByName(name.trim())) {
                    result.addError(lineNumber, "Salle déjà présente : " + name.trim()
                            + ". Ligne ignorée pour ne pas créer de doublon.");
                    continue;
                }
                RoomEntity room = RoomEntity.builder().build();
                room.setName(name.trim());
                room.setActive(true);

                String capacity = value(row, col, "capacity");
                if (!isBlank(capacity)) {
                    try {
                        room.setCapacity(Integer.parseInt(capacity.trim()));
                    } catch (NumberFormatException nfe) {
                        result.addError(lineNumber, "Capacité invalide : " + capacity);
                        continue;
                    }
                }
                roomRepository.save(room);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import salle ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des types de groupe depuis un CSV.
     * Colonnes reconnues : {@code name,size}. Obligatoire : {@code name}.
     */
    @Transactional
    public ImportResultDTO importGroupTypes(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }
        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String name = value(row, col, "name");
                if (isBlank(name)) {
                    result.addError(lineNumber, "Nom du type de groupe obligatoire.");
                    continue;
                }
                if (groupTypeRepository.existsByName(name.trim())) {
                    result.addError(lineNumber, "Type de groupe déjà présent : " + name.trim()
                            + ". Ligne ignorée pour ne pas créer de doublon.");
                    continue;
                }
                GroupTypeEntity groupType = GroupTypeEntity.builder().build();
                groupType.setName(name.trim());
                groupType.setActive(true);

                String size = value(row, col, "size");
                if (!isBlank(size)) {
                    try {
                        groupType.setSize(Integer.parseInt(size.trim()));
                    } catch (NumberFormatException nfe) {
                        result.addError(lineNumber, "Taille (size) invalide : " + size);
                        continue;
                    }
                }
                groupTypeRepository.save(groupType);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import type de groupe ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des élèves depuis un CSV.
     * Colonnes reconnues : {@code firstName,lastName,gender,level,establishment,phoneNumber}.
     * Obligatoires : {@code firstName}, {@code lastName}.
     */
    @Transactional
    public ImportResultDTO importStudents(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }

        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1; // +1 pour l'en-tête, 1-indexé pour l'utilisateur
            String[] row = rows.get(i);
            try {
                String firstName = value(row, col, "firstname");
                String lastName = value(row, col, "lastname");
                if (isBlank(firstName) || isBlank(lastName)) {
                    result.addError(lineNumber, "Prénom et nom obligatoires.");
                    continue;
                }

                StudentEntity student = StudentEntity.builder()
                        .status(StudentStatus.ACTIVE)
                        .build();
                student.setFirstName(firstName.trim());
                student.setLastName(lastName.trim());
                student.setGender(emptyToNull(value(row, col, "gender")));
                student.setPhoneNumber(emptyToNull(value(row, col, "phonenumber")));
                student.setEstablishment(emptyToNull(value(row, col, "establishment")));
                student.setActive(true);

                String levelName = value(row, col, "level");
                if (!isBlank(levelName)) {
                    LevelEntity level = levelRepository.findByName(levelName.trim()).orElse(null);
                    if (level == null) {
                        result.addError(lineNumber, "Niveau introuvable : " + levelName);
                        continue;
                    }
                    student.setLevel(level);
                }

                studentRepository.save(student);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import élève ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des enseignants depuis un CSV.
     * Colonnes reconnues : {@code firstName,lastName,specialization,phoneNumber,email}.
     * Obligatoires : {@code firstName}, {@code lastName}.
     */
    @Transactional
    public ImportResultDTO importTeachers(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }

        Map<String, Integer> col = headerIndex(rows.get(0));
        // Première ligne de chaque enseignant du fichier : une même personne deux fois dans le
        // fichier n'est créée qu'une fois.
        Map<String, Integer> firstLineOf = new HashMap<>();
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String firstName = value(row, col, "firstname");
                String lastName = value(row, col, "lastname");
                if (isBlank(firstName) || isBlank(lastName)) {
                    result.addError(lineNumber, "Prénom et nom obligatoires.");
                    continue;
                }

                String fullName = firstName.trim() + " " + lastName.trim();
                Integer earlierLine = firstLineOf.putIfAbsent(fullName.toLowerCase(Locale.ROOT), lineNumber);
                if (earlierLine != null) {
                    result.addError(lineNumber, "Enseignant en double dans le fichier : " + fullName
                            + " (déjà ligne " + earlierLine + "). Ligne ignorée.");
                    continue;
                }
                // Même refus que pour les référentiels : réimporter le fichier créait une seconde
                // fiche par enseignant, comptée par le tableau de bord et proposée en double.
                if (teacherRepository.existsByFullName(firstName.trim(), lastName.trim())) {
                    result.addError(lineNumber, "Enseignant déjà présent : " + fullName
                            + ". Ligne ignorée pour ne pas créer de doublon.");
                    continue;
                }

                TeacherEntity teacher = TeacherEntity.builder().build();
                teacher.setFirstName(firstName.trim());
                teacher.setLastName(lastName.trim());
                teacher.setSpecialization(emptyToNull(value(row, col, "specialization")));
                teacher.setPhoneNumber(emptyToNull(value(row, col, "phonenumber")));
                teacher.setEmail(emptyToNull(value(row, col, "email")));
                teacher.setActive(true);

                teacherRepository.save(teacher);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import enseignant ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des groupes depuis un CSV. Chaque groupe est rattaché à l'année scolaire courante.
     * Colonnes reconnues :
     * {@code name,groupType,level,subject,teacherFirstName,teacherLastName,sessionNumberPerSerie,price}.
     * Obligatoires : {@code name}, {@code level}, {@code subject}.
     *
     * <p>{@code price} désigne un tarif existant par son montant. Absent, le groupe est créé mais
     * signalé en avertissement : sans tarif, aucun encaissement n'y est possible.</p>
     */
    @Transactional
    public ImportResultDTO importGroups(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }

        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String name = value(row, col, "name");
                String levelName = value(row, col, "level");
                String subjectName = value(row, col, "subject");
                if (isBlank(name) || isBlank(levelName) || isBlank(subjectName)) {
                    result.addError(lineNumber, "Nom, niveau et matière obligatoires.");
                    continue;
                }

                LevelEntity level = levelRepository.findByName(levelName.trim()).orElse(null);
                if (level == null) {
                    result.addError(lineNumber, "Niveau introuvable : " + levelName);
                    continue;
                }

                SubjectEntity subject = subjectRepository.findByNameContaining(subjectName.trim())
                        .stream().findFirst().orElse(null);
                if (subject == null) {
                    result.addError(lineNumber, "Matière introuvable : " + subjectName);
                    continue;
                }

                GroupEntity group = GroupEntity.builder()
                        .name(name.trim())
                        .level(level)
                        .subject(subject)
                        .build();

                // Type de groupe (optionnel), résolu par nom.
                String groupTypeName = value(row, col, "grouptype");
                if (!isBlank(groupTypeName)) {
                    GroupTypeEntity groupType = groupTypeRepository.findByName(groupTypeName.trim())
                            .stream().findFirst().orElse(null);
                    if (groupType == null) {
                        result.addError(lineNumber, "Type de groupe introuvable : " + groupTypeName);
                        continue;
                    }
                    group.setGroupType(groupType);
                }

                // Enseignant (optionnel), résolu par prénom + nom.
                String teacherFirst = value(row, col, "teacherfirstname");
                String teacherLast = value(row, col, "teacherlastname");
                if (!isBlank(teacherFirst) && !isBlank(teacherLast)) {
                    List<TeacherEntity> teachers = teacherRepository
                            .findByFirstNameAndLastName(teacherFirst.trim(), teacherLast.trim());
                    if (teachers.isEmpty()) {
                        result.addError(lineNumber,
                                "Enseignant introuvable : " + teacherFirst + " " + teacherLast);
                        continue;
                    }
                    group.setTeacher(teachers.get(0));
                }

                // Nombre de séances par série (optionnel).
                String sessions = value(row, col, "sessionnumberperserie");
                if (!isBlank(sessions)) {
                    try {
                        group.setSessionNumberPerSerie(Integer.parseInt(sessions.trim()));
                    } catch (NumberFormatException nfe) {
                        result.addError(lineNumber, "Nombre de séances invalide : " + sessions);
                        continue;
                    }
                }

                // Tarif par séance (optionnel), résolu par montant parmi les tarifs existants.
                // Comme le niveau ou la matière, il est désigné et jamais créé ici : un tarif
                // fabriqué au fil d'un import de groupes échapperait à l'administrateur qui gère
                // la grille tarifaire.
                String priceRaw = value(row, col, "price");
                boolean withoutPrice = isBlank(priceRaw);
                if (!withoutPrice) {
                    double priceValue;
                    try {
                        priceValue = Double.parseDouble(priceRaw.trim().replace(",", "."));
                    } catch (NumberFormatException nfe) {
                        result.addError(lineNumber, "Prix invalide : " + priceRaw);
                        continue;
                    }
                    PricingEntity pricing = pricingRepository.findByPriceOrderByIdAsc(priceValue)
                            .stream().findFirst().orElse(null);
                    if (pricing == null) {
                        result.addError(lineNumber, "Tarif introuvable : " + priceRaw.trim()
                                + ". Importez d'abord ce tarif, puis relancez l'import des groupes.");
                        continue;
                    }
                    group.setPrice(pricing);
                }

                group.setActive(true);
                // createGroup rattache automatiquement l'année scolaire courante (Exigence 3.2).
                groupService.createGroup(group);
                result.incrementImported();

                // Un groupe sans tarif est créé, mais aucun encaissement n'y est possible : le
                // coût d'une série se calcule à partir du prix par séance. Le signaler ici, au
                // moment de l'import, évite de le découvrir plus tard devant un parent qui paie.
                // Ce n'est pas une erreur : la colonne est facultative et les fichiers existants
                // doivent continuer à passer.
                if (withoutPrice) {
                    result.addWarning(lineNumber, "Groupe « " + name.trim() + " » créé sans tarif : "
                            + "aucun paiement ne pourra y être encaissé tant qu'un tarif n'est pas "
                            + "rattaché au groupe.");
                }
            } catch (Exception e) {
                LOGGER.warn("Import groupe ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Importe des inscriptions (un élève dans un groupe, à une date d'arrivée) depuis un CSV.
     * Colonnes reconnues : {@code firstName,lastName,group,arrivalDate,phoneNumber}.
     * Obligatoires : {@code firstName}, {@code lastName}, {@code group}, {@code arrivalDate}
     * (AAAA-MM-JJ). Une ligne = une inscription : un élève suivi dans deux groupes a deux lignes.
     *
     * <p><b>Résolution.</b> L'élève est cherché parmi les élèves actifs par prénom et nom, casse et
     * espaces de bord ignorés ; {@code phoneNumber} ne sert qu'à départager des homonymes. Le groupe
     * est cherché par son nom dans l'année scolaire <b>courante</b>, comme l'import des groupes y
     * rattache les siens : un groupe d'une année close n'est jamais résolu.</p>
     *
     * <p><b>Téléphone contredit.</b> Un nom unique désigne l'élève même si le téléphone du fichier
     * ne correspond pas au sien : la ligne est importée, mais signalée en avertissement. Le cas
     * typique est un homonyme dont la ligne a échoué à l'import des élèves : le fichier le désigne
     * par son téléphone, et c'est l'autre qui est inscrit, et doit les séances du groupe. Le refus
     * bloquerait aussi un simple changement de numéro ; le silence ne serait jamais relu. Un élève
     * sans téléphone enregistré reçoit le même avertissement : le téléphone fourni n'a pas pu être
     * vérifié. Sans téléphone dans le fichier, rien n'est signalé : la colonne est facultative.</p>
     *
     * <p><b>Aucune date par défaut.</b> La date d'arrivée décide des séances dues : absente ou
     * invalide, la ligne est refusée et la méthode métier n'est pas appelée (elle prendrait le jour
     * même).</p>
     *
     * <p><b>Contrôles métier.</b> L'inscription passe par {@link StudentGroupService#addStudentsToGroup},
     * le chemin de « Ajouter un étudiant » : niveau, bornes de l'année, départ recouvert, année close
     * et inscription déjà en cours y sont contrôlés, et ne sont pas réimplémentés ici. Son refus est
     * rapporté sur la ligne avec le message du serveur. Seules des inscriptions sont créées : ni
     * présence, ni absence, ni paiement, ni série.</p>
     *
     * <p><b>Pas de transaction englobante</b>, contrairement aux autres imports. Chaque ligne appelle
     * la méthode métier sur un autre bean, qui ouvre et valide sa propre transaction : un refus
     * n'annule que sa ligne. Dans une transaction englobante, le refus traversant le proxy
     * transactionnel la marquerait « rollback-only », et son commit final lèverait
     * {@code UnexpectedRollbackException} : toutes les lignes, valides comprises, seraient perdues.
     * Pour la même raison, la boucle ne manipule que des identifiants et des champs simples, relus à
     * chaque ligne.</p>
     */
    public ImportResultDTO importEnrolments(MultipartFile file) {
        ImportResultDTO result = new ImportResultDTO();
        List<String[]> rows = readCsv(file, result);
        if (rows.isEmpty()) {
            return result;
        }

        Optional<SchoolYearEntity> currentYear = currentSchoolYearService.findCurrent();
        if (currentYear.isEmpty()) {
            result.addError(0, "Aucune année scolaire courante définie : les inscriptions visent les "
                    + "groupes de l'année courante. Désignez l'année courante, puis relancez l'import.");
            return result;
        }
        Long schoolYearId = currentYear.get().getId();
        String schoolYearLabel = currentYear.get().getLabel();

        Map<String, Integer> col = headerIndex(rows.get(0));
        for (int i = 1; i < rows.size(); i++) {
            int lineNumber = i + 1;
            String[] row = rows.get(i);
            try {
                String firstName = value(row, col, "firstname");
                String lastName = value(row, col, "lastname");
                String groupName = value(row, col, "group");
                String arrivalRaw = value(row, col, "arrivaldate");

                List<String> missing = new ArrayList<>();
                if (isBlank(firstName)) {
                    missing.add("firstName");
                }
                if (isBlank(lastName)) {
                    missing.add("lastName");
                }
                if (isBlank(groupName)) {
                    missing.add("group");
                }
                if (isBlank(arrivalRaw)) {
                    missing.add("arrivalDate");
                }
                if (!missing.isEmpty()) {
                    String message = "Obligatoire(s) manquant(s) : " + String.join(", ", missing) + ".";
                    if (missing.contains("arrivalDate")) {
                        message += " La date d'arrivée n'a pas de valeur par défaut : c'est elle qui "
                                + "décide des séances dues.";
                    }
                    result.addError(lineNumber, message);
                    continue;
                }

                LocalDate arrival;
                try {
                    arrival = LocalDate.parse(arrivalRaw.trim(), ARRIVAL_DATE_FORMAT);
                } catch (DateTimeParseException dtpe) {
                    result.addError(lineNumber, "Date d'arrivée invalide : " + arrivalRaw.trim()
                            + " (format attendu AAAA-MM-JJ, par exemple 2026-09-15).");
                    continue;
                }

                String fullName = firstName.trim() + " " + lastName.trim();
                StudentEntity student = resolveStudent(lineNumber, row, col, firstName, lastName,
                        fullName, result);
                if (student == null) {
                    continue;
                }
                // Calculé avant l'appel métier : la boucle ne relit aucune entité après la transaction
                // de la ligne.
                String phoneWarning = phoneWarning(student, value(row, col, "phonenumber"), fullName);

                List<GroupEntity> groups = groupRepository
                        .findActiveBySchoolYearIdAndName(schoolYearId, groupName.trim());
                if (groups.isEmpty()) {
                    result.addError(lineNumber, "Groupe introuvable dans l'année scolaire courante ("
                            + schoolYearLabel + ") : " + groupName.trim() + ". Importez d'abord les groupes ; "
                            + "un groupe d'une année close ne reçoit plus d'inscription.");
                    continue;
                }
                if (groups.size() > 1) {
                    result.addError(lineNumber, "Plusieurs groupes « " + groupName.trim()
                            + " » dans l'année scolaire courante (" + schoolYearLabel
                            + ") : renommez-les pour les distinguer.");
                    continue;
                }
                GroupEntity group = groups.get(0);

                // Chemin de « Ajouter un étudiant » : tous les contrôles métier y sont faits.
                int created = studentGroupService.addStudentsToGroup(StudentGroupDTO.builder()
                        .groupId(group.getId())
                        .studentIds(List.of(student.getId()))
                        .dateAssigned(arrival)
                        .build());
                if (created == 0) {
                    result.addError(lineNumber, "Déjà inscrit : " + fullName + " suit déjà le groupe « "
                            + group.getName() + " » (inscription en cours). Ligne ignorée pour ne pas "
                            + "créer de doublon.");
                    continue;
                }
                result.incrementImported();
                // Seulement sur une ligne importée : une ligne refusée porte déjà son erreur.
                if (phoneWarning != null) {
                    result.addWarning(lineNumber, phoneWarning);
                }
            } catch (CustomServiceException e) {
                // Refus métier (niveau, bornes de l'année, départ recouvert, année close) : le serveur
                // en nomme la cause, reprise telle quelle.
                result.addError(lineNumber, "Inscription refusée : " + e.getMessage());
            } catch (Exception e) {
                LOGGER.warn("Import inscription ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
    }

    /**
     * Élève actif désigné par la ligne, ou {@code null} après avoir rapporté pourquoi il ne peut
     * pas l'être. Un nom unique suffit ; le téléphone ne départage que des homonymes (s'il contredit
     * un nom unique, la ligne passe avec un avertissement : {@code phoneWarning}).
     */
    private StudentEntity resolveStudent(int lineNumber, String[] row, Map<String, Integer> col,
                                         String firstName, String lastName, String fullName,
                                         ImportResultDTO result) {
        List<StudentEntity> namesakes = studentRepository
                .findByFullNameAndStatus(firstName.trim(), lastName.trim(), StudentStatus.ACTIVE);
        if (namesakes.isEmpty()) {
            result.addError(lineNumber, "Élève introuvable parmi les élèves actifs : " + fullName
                    + ". Importez d'abord les élèves.");
            return null;
        }
        if (namesakes.size() == 1) {
            return namesakes.get(0);
        }

        String phone = value(row, col, "phonenumber");
        String wanted = normalizePhone(phone);
        if (wanted.isEmpty()) {
            result.addError(lineNumber, "Plusieurs élèves actifs s'appellent " + fullName + " ("
                    + namesakes.size() + ") : renseignez la colonne phoneNumber pour désigner le bon.");
            return null;
        }
        List<StudentEntity> matching = namesakes.stream()
                .filter(s -> wanted.equals(normalizePhone(s.getPhoneNumber())))
                .toList();
        if (matching.isEmpty()) {
            result.addError(lineNumber, "Plusieurs élèves actifs s'appellent " + fullName
                    + ", mais aucun n'a le téléphone " + phone.trim() + ".");
            return null;
        }
        if (matching.size() > 1) {
            result.addError(lineNumber, "Plusieurs élèves actifs s'appellent " + fullName
                    + " et ont le téléphone " + phone.trim() + " : inscrivez-le depuis la fiche du groupe "
                    + "(Ajouter un étudiant).");
            return null;
        }
        return matching.get(0);
    }

    /**
     * Avertissement à porter sur la ligne quand le téléphone du fichier ne confirme pas l'élève
     * retenu, ou {@code null}. Seul un nom unique peut y conduire : entre homonymes, l'élève retenu
     * a par construction le téléphone du fichier.
     */
    private static String phoneWarning(StudentEntity student, String phone, String fullName) {
        String given = normalizePhone(phone);
        if (given.isEmpty()) {
            return null;
        }
        String stored = normalizePhone(student.getPhoneNumber());
        if (stored.isEmpty()) {
            return "Téléphone non vérifié : " + fullName + " est inscrit, mais sa fiche n'a aucun "
                    + "téléphone à comparer à celui du fichier (" + phone.trim() + "). Vérifiez qu'il "
                    + "s'agit du bon élève.";
        }
        if (!given.equals(stored)) {
            return "Téléphone différent : " + fullName + " est inscrit, mais le téléphone du fichier ("
                    + phone.trim() + ") ne correspond pas à celui de sa fiche (" + student.getPhoneNumber().trim()
                    + "). Vérifiez qu'il s'agit du bon élève.";
        }
        return null;
    }

    /** Téléphone comparable : espaces, points, tirets et parenthèses retirés. */
    private static String normalizePhone(String phone) {
        return phone == null ? "" : phone.replaceAll("[\\s.\\-()]", "");
    }

    // ==========================================================================================
    // Helpers CSV
    // ==========================================================================================

    /**
     * Lit le fichier CSV en lignes de champs. Détecte le séparateur (`;` s'il est présent dans
     * l'en-tête, sinon `,`). Renseigne une erreur globale (ligne 0) en cas de fichier illisible.
     */
    private List<String[]> readCsv(MultipartFile file, ImportResultDTO result) {
        List<String[]> rows = new ArrayList<>();
        if (file == null || file.isEmpty()) {
            result.addError(0, "Fichier vide ou absent.");
            return rows;
        }
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String headerLine = reader.readLine();
            // BOM éventuel en début de fichier (UTF-8 avec BOM : Excel sous Windows en a besoin pour
            // afficher les accents). Le décodeur UTF-8 de Java le laisse dans le texte : non retiré,
            // il colle à la première colonne (« \uFEFFfirstName ») et chaque ligne est refusée.
            // Retiré AVANT le contrôle de l'en-tête : un fichier réduit à son BOM est vide.
            if (headerLine != null) {
                headerLine = headerLine.replace("\uFEFF", "");
            }
            if (headerLine == null || headerLine.isBlank()) {
                result.addError(0, "En-tête CSV manquant.");
                return rows;
            }
            char separator = headerLine.contains(";") ? ';' : ',';

            rows.add(split(headerLine, separator));
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                rows.add(split(line, separator));
            }
        } catch (IOException e) {
            result.addError(0, "Lecture du fichier impossible : " + e.getMessage());
        }
        return rows;
    }

    /** Découpe une ligne CSV (guillemets simples gérés a minima). */
    private String[] split(String line, char separator) {
        List<String> fields = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == separator && !inQuotes) {
                fields.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString());
        return fields.toArray(new String[0]);
    }

    /** Indexe les colonnes de l'en-tête par nom normalisé (minuscules, sans espaces). */
    private Map<String, Integer> headerIndex(String[] header) {
        Map<String, Integer> index = new HashMap<>();
        for (int i = 0; i < header.length; i++) {
            String key = header[i] == null ? "" : header[i].trim().toLowerCase().replace(" ", "");
            index.put(key, i);
        }
        return index;
    }

    /** Valeur d'une colonne par nom normalisé, ou chaîne vide si absente. */
    private String value(String[] row, Map<String, Integer> col, String columnName) {
        Integer idx = col.get(columnName);
        if (idx == null || idx >= row.length) {
            return "";
        }
        String raw = row[idx];
        return raw == null ? "" : raw.trim();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private String emptyToNull(String s) {
        return isBlank(s) ? null : s.trim();
    }
}
