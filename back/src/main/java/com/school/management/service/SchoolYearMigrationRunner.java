package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.SchoolYearEntity;
import com.school.management.persistance.StudentEntity;
import com.school.management.persistance.StudentStatus;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.SchoolYearRepository;
import com.school.management.repository.StudentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Migration unique et <strong>idempotente</strong> de l'existant vers le modèle « année
 * scolaire » (Option A).
 *
 * <p>Exécutée au démarrage de l'application ({@link ApplicationRunner}). Elle ne fait rien si
 * une année scolaire existe déjà, de sorte que les redéploiements et redémarrages ne créent
 * aucun doublon (Exigence 12.1). Sinon elle :</p>
 * <ol>
 *   <li>crée l'année scolaire initiale et la marque comme courante (Exigence 12.1) ;</li>
 *   <li>rattache chaque groupe sans année scolaire à l'année initiale (Exigences 12.2, 12.5) ;</li>
 *   <li>positionne le statut de chaque étudiant sans statut à {@code ACTIVE} (Exigence 12.4).</li>
 * </ol>
 *
 * <p>Les séries, séances, paiements et présences ne sont <strong>pas</strong> touchés : leur
 * année scolaire est dérivée via leur groupe, sans référence directe (Exigence 12.3).</p>
 *
 * <p>Le libellé de l'année initiale peut être fourni via la propriété
 * {@code school.year.initial-label} ; à défaut, un libellé raisonnable est dérivé de la date du
 * jour (l'année scolaire bascule au 1er septembre).</p>
 */
@Component
public class SchoolYearMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SchoolYearMigrationRunner.class);

    /** Motif d'un libellé d'année scolaire : quatre chiffres, un tiret, quatre chiffres. */
    private static final Pattern LABEL_PATTERN = Pattern.compile("^(\\d{4})-(\\d{4})$");

    /** Mois (base 1) à partir duquel l'année scolaire suivante commence (septembre). */
    private static final int SCHOOL_YEAR_START_MONTH = Calendar.SEPTEMBER;

    private final SchoolYearRepository schoolYearRepository;
    private final GroupRepository groupRepository;
    private final StudentRepository studentRepository;

    /**
     * Libellé de l'année scolaire initiale, configurable via la propriété
     * {@code school.year.initial-label}. Vide par défaut : un libellé dérivé de la date du jour
     * est alors utilisé.
     */
    @Value("${school.year.initial-label:}")
    private String initialLabelProperty;

    @Autowired
    public SchoolYearMigrationRunner(SchoolYearRepository schoolYearRepository,
                                     GroupRepository groupRepository,
                                     StudentRepository studentRepository) {
        this.schoolYearRepository = schoolYearRepository;
        this.groupRepository = groupRepository;
        this.studentRepository = studentRepository;
    }

    /**
     * Point d'entrée exécuté au démarrage. Délègue à {@link #migrate()} au sein d'une transaction.
     */
    @Override
    public void run(ApplicationArguments args) {
        migrate();
    }

    /**
     * Exécute la migration si aucune année scolaire n'existe encore (idempotence).
     *
     * @return l'année scolaire initiale créée, ou {@code null} si la migration a été ignorée.
     */
    @Transactional
    public SchoolYearEntity migrate() {
        // 1) Idempotence : si au moins une année scolaire existe déjà, ne rien faire (Exigence 12.1).
        if (schoolYearRepository.count() > 0) {
            log.debug("Migration année scolaire ignorée : une année scolaire existe déjà.");
            return null;
        }

        // 2) Créer l'année scolaire initiale et la marquer comme courante (Exigence 12.1).
        String label = resolveInitialLabel();
        SchoolYearEntity initialYear = SchoolYearEntity.builder()
                .label(label)
                .startDate(deriveStartDate(label))
                .endDate(deriveEndDate(label))
                .isCurrent(true)
                .build();
        initialYear = schoolYearRepository.save(initialYear);
        log.info("Migration année scolaire : année initiale « {} » créée et marquée courante.", label);

        // 3) Rattacher chaque groupe sans année scolaire à l'année initiale (Exigences 12.2, 12.5).
        List<GroupEntity> groupsWithoutYear = groupRepository.findBySchoolYearIsNull();
        for (GroupEntity group : groupsWithoutYear) {
            group.setSchoolYear(initialYear);
        }
        if (!groupsWithoutYear.isEmpty()) {
            groupRepository.saveAll(groupsWithoutYear);
        }
        log.info("Migration année scolaire : {} groupe(s) rattaché(s) à l'année initiale.",
                groupsWithoutYear.size());

        // 4) Positionner à ACTIVE le statut de chaque étudiant sans statut (Exigence 12.4).
        //    On parcourt tous les étudiants et on ne modifie que ceux dont le statut est nul :
        //    une requête dérivée findByStatus(null) générerait « status = null » (jamais vrai
        //    en SQL) et manquerait justement les statuts nuls à corriger.
        List<StudentEntity> studentsWithoutStatus = studentRepository.findAll().stream()
                .filter(student -> student != null && student.getStatus() == null)
                .toList();
        for (StudentEntity student : studentsWithoutStatus) {
            student.setStatus(StudentStatus.ACTIVE);
        }
        if (!studentsWithoutStatus.isEmpty()) {
            studentRepository.saveAll(studentsWithoutStatus);
        }
        log.info("Migration année scolaire : {} étudiant(s) passé(s) au statut ACTIVE.",
                studentsWithoutStatus.size());

        // 5) Séries / séances / paiements / présences ne sont pas touchés : leur année est
        //    dérivée via leur groupe (Exigence 12.3).
        return initialYear;
    }

    /**
     * Détermine le libellé de l'année initiale : celui fourni par la propriété
     * {@code school.year.initial-label} s'il est renseigné et valide, sinon un libellé dérivé de
     * la date du jour.
     */
    private String resolveInitialLabel() {
        if (initialLabelProperty != null && !initialLabelProperty.isBlank()) {
            return initialLabelProperty.trim();
        }
        return deriveDefaultLabel();
    }

    /**
     * Dérive un libellé par défaut « YYYY-YYYY » à partir de la date du jour : l'année scolaire
     * commence au 1er septembre. Ainsi, avant septembre l'année scolaire est
     * {@code (année-1)-année}, à partir de septembre {@code année-(année+1)}.
     */
    private String deriveDefaultLabel() {
        Calendar now = Calendar.getInstance();
        int year = now.get(Calendar.YEAR);
        int month = now.get(Calendar.MONTH); // base 0 (Calendar.SEPTEMBER == 8)

        int firstYear = (month >= SCHOOL_YEAR_START_MONTH) ? year : year - 1;
        return firstYear + "-" + (firstYear + 1);
    }

    /**
     * Dérive la date de début (1er septembre de la première année du libellé). Retourne
     * {@code null} si le libellé est mal formé (la validation de {@code SchoolYearService} n'est
     * pas sollicitée ici, mais l'entité exige des dates non nulles).
     */
    private Date deriveStartDate(String label) {
        Integer firstYear = firstYearOf(label);
        if (firstYear == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(firstYear, Calendar.SEPTEMBER, 1);
        return calendar.getTime();
    }

    /**
     * Dérive la date de fin (30 juin de la seconde année du libellé).
     */
    private Date deriveEndDate(String label) {
        Integer firstYear = firstYearOf(label);
        if (firstYear == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(firstYear + 1, Calendar.JUNE, 30);
        return calendar.getTime();
    }

    /**
     * Extrait la première année d'un libellé « YYYY-YYYY », ou {@code null} si le libellé est
     * mal formé.
     */
    private Integer firstYearOf(String label) {
        if (label == null) {
            return null;
        }
        Matcher matcher = LABEL_PATTERN.matcher(label);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
