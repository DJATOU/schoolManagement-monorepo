package com.school.management.service.importcsv;

import com.school.management.dto.importcsv.ImportResultDTO;
import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.GroupTypeEntity;
import com.school.management.persistance.LevelEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.persistance.PricingEntity;
import com.school.management.persistance.RoomEntity;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
 */
@Service
public class CsvImportService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CsvImportService.class);

    private final StudentRepository studentRepository;
    private final TeacherRepository teacherRepository;
    private final LevelRepository levelRepository;
    private final SubjectRepository subjectRepository;
    private final GroupTypeRepository groupTypeRepository;
    private final RoomRepository roomRepository;
    private final PricingRepository pricingRepository;
    private final GroupServiceImpl groupService;

    @Autowired
    public CsvImportService(StudentRepository studentRepository,
                            TeacherRepository teacherRepository,
                            LevelRepository levelRepository,
                            SubjectRepository subjectRepository,
                            GroupTypeRepository groupTypeRepository,
                            RoomRepository roomRepository,
                            PricingRepository pricingRepository,
                            GroupServiceImpl groupService) {
        this.studentRepository = studentRepository;
        this.teacherRepository = teacherRepository;
        this.levelRepository = levelRepository;
        this.subjectRepository = subjectRepository;
        this.groupTypeRepository = groupTypeRepository;
        this.roomRepository = roomRepository;
        this.pricingRepository = pricingRepository;
        this.groupService = groupService;
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
     * {@code name,groupType,level,subject,teacherFirstName,teacherLastName,sessionNumberPerSerie}.
     * Obligatoires : {@code name}, {@code level}, {@code subject}.
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

                group.setActive(true);
                // createGroup rattache automatiquement l'année scolaire courante (Exigence 3.2).
                groupService.createGroup(group);
                result.incrementImported();
            } catch (Exception e) {
                LOGGER.warn("Import groupe ligne {} en échec : {}", lineNumber, e.getMessage());
                result.addError(lineNumber, e.getMessage());
            }
        }
        return result;
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
            if (headerLine == null || headerLine.isBlank()) {
                result.addError(0, "En-tête CSV manquant.");
                return rows;
            }
            // BOM éventuel en début de fichier.
            headerLine = headerLine.replace("\uFEFF", "");
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
