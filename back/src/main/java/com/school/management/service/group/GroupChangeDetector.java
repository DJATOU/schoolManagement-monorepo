package com.school.management.service.group;

import com.school.management.persistance.GroupEntity;
import com.school.management.persistance.StudentGroupEntity;
import com.school.management.repository.AttendanceRepository;
import com.school.management.repository.StudentGroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Signalement d'un changement de groupe en cours de mois (exigences 10.1 et 10.3).
 *
 * <h2>Pourquoi ce composant existe</h2>
 * L'unité de facturation est la <strong>série</strong> : aucune agrégation automatique entre
 * groupes sur un mois civil n'est effectuée. Un étudiant qui quitte un groupe et en rejoint un
 * autre au milieu d'un mois voit donc chaque série facturée indépendamment, et l'ajustement est
 * administratif. Ce détecteur est la contrepartie obligatoire de cette décision : il rend le cas
 * <em>visible</em>, à défaut de le traiter.
 *
 * <h2>Ce qui est détecté — et ce qui ne l'est pas</h2>
 * Le signalement porte sur un <strong>changement</strong> de groupe : une inscription clôturée
 * dans un groupe et une inscription ouverte dans un <em>autre</em> groupe, les deux événements
 * tombant dans le même mois civil.
 *
 * <p>Il ne porte <strong>pas</strong> sur l'appartenance simultanée à plusieurs groupes. Un
 * étudiant suivant maths, physique et arabe a trois inscriptions actives en permanence : c'est la
 * situation normale. La signaler produirait une alerte permanente que l'administrateur cesserait
 * de lire, ce qui reviendrait à ne rien signaler du tout (exigence 10.4).</p>
 *
 * <p>C'est aussi pourquoi la détection s'appuie sur les <strong>inscriptions</strong>
 * ({@code student_groups}) et non sur les présences. Des présences dans deux groupes sur un même
 * mois sont le lot commun d'un étudiant multi-matières, et un rattrapage en produit sans qu'aucun
 * changement n'ait eu lieu.</p>
 *
 * <h2>Comment une clôture est reconnue</h2>
 * Une inscription clôturée est une ligne de {@code student_groups} dont {@code active} vaut
 * <strong>faux</strong> : {@code StudentGroupService.removeStudentFromGroup} désactive la ligne
 * au lieu de la supprimer, ce qui préserve l'historique. Il n'existe pas de colonne de date de
 * fin ; la date de clôture est donc {@code date_update}, horodatée par {@code @PreUpdate} au
 * moment de la désactivation. Une ligne dont {@code active} est nul est héritée et considérée
 * <em>active</em>, comme partout ailleurs dans le dépôt.
 *
 * <h2>Lecture seule, hors du chemin d'encaissement</h2>
 * Aucune écriture, aucune dépendance depuis {@code PaymentProcessingService} : le détecteur
 * n'ajoute pas de latence à l'encaissement et ne peut pas le bloquer (exigences 10.6, 10.7). Il
 * ne participe à aucun calcul monétaire, donc il ne peut fausser aucun montant.
 */
@Service
public class GroupChangeDetector {

    private final StudentGroupRepository studentGroupRepository;
    private final AttendanceRepository attendanceRepository;

    public GroupChangeDetector(StudentGroupRepository studentGroupRepository,
                               AttendanceRepository attendanceRepository) {
        this.studentGroupRepository = studentGroupRepository;
        this.attendanceRepository = attendanceRepository;
    }

    /**
     * Changements de groupe d'un étudiant, du plus ancien au plus récent.
     *
     * @param studentId identifiant de l'étudiant
     * @return les signalements, liste vide lorsqu'aucun changement n'est détecté
     */
    @Transactional(readOnly = true)
    public List<GroupChange> detect(Long studentId) {
        Objects.requireNonNull(studentId, "studentId ne doit pas être nul.");

        List<StudentGroupEntity> enrolments = studentGroupRepository.findByStudentId(studentId);
        List<Event> closures = new ArrayList<>();
        List<Event> openings = new ArrayList<>();
        collectEvents(enrolments, closures, openings);

        // Sans clôture, il n'y a rien à signaler : c'est le cas de l'étudiant multi-matières
        // (exigence 10.4). Sortir ici évite en plus toute requête de comptage.
        if (closures.isEmpty()) {
            return List.of();
        }

        Map<GroupMonth, Integer> attendedCache = new HashMap<>();
        Set<String> seen = new HashSet<>();
        List<GroupChange> changes = new ArrayList<>();

        for (Event closure : sorted(closures)) {
            for (Event opening : sorted(openings)) {
                if (!isGroupChange(closure, opening) || !seen.add(key(closure, opening))) {
                    continue;
                }
                YearMonth month = closure.month();
                changes.add(new GroupChange(
                        month.getYear(), month.getMonthValue(),
                        activity(studentId, closure, month, attendedCache),
                        activity(studentId, opening, month, attendedCache)));
            }
        }
        return List.copyOf(changes);
    }

    /**
     * Répartit les inscriptions en clôtures et en ouvertures datées au mois.
     *
     * <p>Une inscription clôturée reste une ouverture candidate : un étudiant peut rejoindre un
     * groupe puis le quitter, et le départ d'un troisième groupe le même mois reste un
     * changement. Une inscription non datable est écartée du lot correspondant — sans date, on ne
     * peut pas affirmer que les deux événements tombent dans le même mois.</p>
     */
    private void collectEvents(List<StudentGroupEntity> enrolments,
                               List<Event> closures, List<Event> openings) {
        for (StudentGroupEntity enrolment : enrolments) {
            GroupEntity group = enrolment == null ? null : enrolment.getGroup();
            if (group == null || group.getId() == null) {
                continue;
            }
            YearMonth openedIn = monthOf(enrolment.getDateAssigned());
            if (openedIn != null) {
                openings.add(new Event(group.getId(), group.getName(), openedIn));
            }
            if (!Boolean.FALSE.equals(enrolment.getActive())) {
                continue;
            }
            YearMonth closedIn = monthOf(enrolment.getDateUpdate());
            if (closedIn != null) {
                closures.add(new Event(group.getId(), group.getName(), closedIn));
            }
        }
    }

    /** Clôture et ouverture dans deux groupes distincts, sur le même mois civil (exigence 10.1). */
    private boolean isGroupChange(Event closure, Event opening) {
        return !closure.groupId().equals(opening.groupId())
                && closure.month().equals(opening.month());
    }

    /** Séances suivies dans le groupe sur le mois du signalement (exigence 10.3). */
    private GroupActivity activity(Long studentId, Event event, YearMonth month,
                                   Map<GroupMonth, Integer> cache) {
        int attended = cache.computeIfAbsent(new GroupMonth(event.groupId(), month),
                key -> countAttended(studentId, key));
        return new GroupActivity(event.groupId(), event.groupName(), attended);
    }

    private int countAttended(Long studentId, GroupMonth key) {
        YearMonth month = key.month();
        Date from = toDate(month.atDay(1).atStartOfDay());
        Date to = toDate(month.atEndOfMonth().atTime(LocalTime.MAX));
        return (int) attendanceRepository.countPresentForStudentAndGroupBetween(
                studentId, key.groupId(), from, to);
    }

    /** Ordre déterministe : mois croissant, puis identifiant de groupe. */
    private List<Event> sorted(List<Event> events) {
        return events.stream()
                .sorted(Comparator.comparing(Event::month).thenComparing(Event::groupId))
                .toList();
    }

    private String key(Event closure, Event opening) {
        return closure.month() + "|" + closure.groupId() + "|" + opening.groupId();
    }

    private YearMonth monthOf(Date date) {
        return date == null ? null
                : YearMonth.from(LocalDate.ofInstant(date.toInstant(), ZoneId.systemDefault()));
    }

    private YearMonth monthOf(LocalDateTime dateTime) {
        return dateTime == null ? null : YearMonth.from(dateTime);
    }

    private Date toDate(LocalDateTime dateTime) {
        return Date.from(dateTime.atZone(ZoneId.systemDefault()).toInstant());
    }

    /** Un événement d'inscription daté au mois : clôture ou ouverture. */
    private record Event(Long groupId, String groupName, YearMonth month) {
    }

    /** Clé de cache du décompte de présences, pour ne compter qu'une fois par groupe et mois. */
    private record GroupMonth(Long groupId, YearMonth month) {
    }

    /**
     * Signalement d'un changement de groupe sur un mois civil.
     *
     * <p>Le mois est exposé en deux entiers plutôt qu'en {@code YearMonth} : le signalement est
     * destiné à l'interface, et deux entiers traversent la sérialisation JSON sans dépendre d'un
     * module de dates.</p>
     *
     * @param year        année civile du changement
     * @param month       mois civil du changement, de 1 à 12
     * @param leftGroup   groupe quitté et ses séances suivies sur ce mois
     * @param joinedGroup groupe rejoint et ses séances suivies sur ce mois
     */
    public record GroupChange(int year, int month, GroupActivity leftGroup,
                              GroupActivity joinedGroup) {

        /** Le mois civil concerné, pour les appelants qui raisonnent en {@code java.time}. */
        public YearMonth yearMonth() {
            return YearMonth.of(year, month);
        }
    }

    /**
     * Activité de l'étudiant dans un groupe sur le mois du signalement.
     *
     * @param groupId       identifiant du groupe
     * @param groupName     nom du groupe, pour que l'interface le nomme sans relire la base
     * @param attendedCount séances suivies (présent) dans ce groupe sur le mois
     */
    public record GroupActivity(Long groupId, String groupName, int attendedCount) {
    }
}
