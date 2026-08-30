package com.school.management.service;

import com.school.management.persistance.GroupEntity;
import com.school.management.repository.GroupRepository;
import com.school.management.repository.StudentGroupRepository;
import com.school.management.service.exception.CustomServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Routage d'une présence hors groupe : vrai rattrapage, ou séance facturée sur place ?
 *
 * <h2>Ce que ce service décide, et ce qu'il ne décide pas</h2>
 * Il répond à une seule question : <strong>« cette séance est-elle déjà facturée ailleurs à cet
 * étudiant ? »</strong> Le test est l'existence d'une inscription à un autre groupe de
 * <strong>même niveau et même matière</strong>, dans la même année scolaire. Si un tel groupe
 * existe, une place y est réservée et la facturation reste ancrée à la séance manquée dans son
 * groupe d'origine ; sinon aucune place n'est réservée nulle part, et la séance est facturée au
 * groupe d'accueil comme pour un membre.
 *
 * <p>Il ne décide <strong>pas</strong> où un rattrapage est autorisé à se dérouler : c'est
 * {@code CatchUpService.isCompatible}, qui compare l'année scolaire, le <em>type de groupe</em> et
 * le prix par séance. Les deux tests coexistent et ne doivent pas être confondus :</p>
 * <ul>
 *   <li>ici — niveau + matière → <em>est-ce</em> un rattrapage (routage, donc facturation) ;</li>
 *   <li>là — type + prix → <em>où</em> le rattrapage peut avoir lieu (compatibilité de la séance
 *       d'accueil).</li>
 * </ul>
 *
 * <h2>« Type de groupe » est un faux ami</h2>
 * {@code GroupTypeEntity} désigne l'<strong>effectif</strong> (petit, moyen, grand, individuel), et
 * n'a aucun rapport avec la paire niveau + matière testée ici. Un groupe de maths de 1re année et un
 * groupe d'anglais de 3e année peuvent partager le même type. Router sur le type facturerait au
 * mauvais groupe ; ce service ne le consulte donc jamais.
 *
 * <h2>Pourquoi le routage vit côté serveur</h2>
 * L'écran de validation d'une séance décidait lui-même du verdict, en marquant rattrapage tout
 * étudiant non membre du groupe. Une décision monétaire prise dans le navigateur n'est ni testable
 * ni fiable, et celle-ci était systématiquement incomplète : aucune séance manquée n'était
 * demandée, si bien que la facturation retombait sur le groupe d'accueil sans que personne ne
 * l'ait choisi. Le client soumet désormais la présence, le serveur la classe.
 */
@Service
public class CatchUpRoutingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CatchUpRoutingService.class);

    private final StudentGroupRepository studentGroupRepository;
    private final GroupRepository groupRepository;

    public CatchUpRoutingService(StudentGroupRepository studentGroupRepository,
                                 GroupRepository groupRepository) {
        this.studentGroupRepository = studentGroupRepository;
        this.groupRepository = groupRepository;
    }

    /**
     * Verdict du routage.
     *
     * <p>Deux valeurs, et volontairement pas de troisième : l'absence de groupe de même niveau et
     * même matière n'est pas une donnée manquante à préciser, c'est une situation parfaitement
     * définie — l'étudiant consomme une séance que personne d'autre ne lui facture.</p>
     */
    public enum RoutingVerdict {

        /**
         * Vrai rattrapage : une place est réservée à cet étudiant dans un autre groupe de même
         * niveau et même matière. La séance manquée doit être désignée, et la facturation reste
         * ancrée à cette séance dans son groupe d'origine.
         */
        TRUE_CATCH_UP,

        /**
         * Aucun groupe de même niveau et même matière : la séance est facturée au groupe
         * d'accueil, comme pour un membre. Aucune séance manquée n'est attendue — son absence est
         * ici légitime, et non un lien oublié.
         */
        HOST_BILLED
    }

    /**
     * Route une présence hors groupe à partir du seul couple (niveau, matière) du groupe d'accueil,
     * borné à son année scolaire.
     *
     * <p>Un groupe d'accueil dont le niveau, la matière ou l'année scolaire n'est pas renseigné ne
     * peut pas fonder de correspondance : le verdict est alors {@link RoutingVerdict#HOST_BILLED}.
     * Ce repli va dans le même sens que celui du qualificateur de rattrapage — facturer plutôt que
     * perdre silencieusement une recette — et il est ici le seul défendable : sans niveau ni
     * matière, rien ne permet d'affirmer qu'une place est réservée ailleurs.</p>
     *
     * @param studentId   identifiant de l'étudiant, non nul
     * @param hostGroupId identifiant du groupe de la séance suivie, non nul
     * @return le verdict de routage
     * @throws CustomServiceException 404 si le groupe d'accueil est introuvable
     */
    @Transactional(readOnly = true)
    public RoutingVerdict route(Long studentId, Long hostGroupId) {
        Objects.requireNonNull(studentId, "L'identifiant de l'étudiant ne doit pas être nul.");
        Objects.requireNonNull(hostGroupId, "L'identifiant du groupe d'accueil ne doit pas être nul.");

        GroupEntity hostGroup = groupRepository.findById(hostGroupId)
                .orElseThrow(() -> new CustomServiceException(
                        "Groupe introuvable pour l'identifiant : " + hostGroupId,
                        HttpStatus.NOT_FOUND));

        Long levelId = idOfLevel(hostGroup);
        Long subjectId = idOfSubject(hostGroup);
        Long schoolYearId = idOfSchoolYear(hostGroup);

        if (levelId == null || subjectId == null || schoolYearId == null) {
            LOGGER.info("Groupe d'accueil {} sans niveau, matière ou année scolaire : "
                            + "la séance est facturée sur place pour l'étudiant {}.",
                    hostGroupId, studentId);
            return RoutingVerdict.HOST_BILLED;
        }

        boolean sameLevelAndSubject = studentGroupRepository.existsEnrolmentInSameLevelAndSubject(
                studentId, hostGroupId, levelId, subjectId, schoolYearId);

        LOGGER.debug("Routage rattrapage — étudiant {}, groupe d'accueil {} (niveau {}, matière {}, "
                        + "année {}) : {}",
                studentId, hostGroupId, levelId, subjectId, schoolYearId,
                sameLevelAndSubject ? "vrai rattrapage" : "facturée sur place");

        return sameLevelAndSubject ? RoutingVerdict.TRUE_CATCH_UP : RoutingVerdict.HOST_BILLED;
    }

    private Long idOfLevel(GroupEntity group) {
        return group.getLevel() == null ? null : group.getLevel().getId();
    }

    private Long idOfSubject(GroupEntity group) {
        return group.getSubject() == null ? null : group.getSubject().getId();
    }

    private Long idOfSchoolYear(GroupEntity group) {
        return group.getSchoolYear() == null ? null : group.getSchoolYear().getId();
    }
}
