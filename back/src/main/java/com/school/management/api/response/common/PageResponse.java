package com.school.management.api.response.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;

/**
 * Wrapper générique pour les réponses paginées de l'API REST.
 *
 * Encapsule les données paginées avec leurs métadonnées dans un format
 * standard et cohérent pour tous les endpoints.
 *
 * Structure JSON de la réponse:
 * <pre>
 * {
 *   "content": [...],
 *   "metadata": {
 *     "page": 0,
 *     "size": 20,
 *     "totalElements": 150,
 *     "totalPages": 8,
 *     "first": true,
 *     "last": false,
 *     "empty": false
 *   }
 * }
 * </pre>
 *
 * Usage dans les controllers:
 * <pre>
 * @GetMapping
 * public ResponseEntity<PageResponse<StudentDTO>> getStudents(Pageable pageable) {
 *     Page<StudentDTO> students = studentService.findAll(pageable);
 *     return ResponseEntity.ok(PageResponse.of(students));
 * }
 * </pre>
 *
 * @param <T> le type d'objet contenu dans la page
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PageResponse<T> {

    /**
     * Liste des éléments de la page actuelle
     */
    private List<T> content;

    /**
     * Métadonnées de pagination
     */
    private PageMetadata metadata;

    /**
     * Métadonnées de pagination contenant les informations
     * sur la page actuelle, le nombre total d'éléments, etc.
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PageMetadata {

        /**
         * Numéro de la page actuelle (commence à 0)
         */
        private int page;

        /**
         * Nombre d'éléments par page
         */
        private int size;

        /**
         * Nombre total d'éléments dans toutes les pages
         */
        private long totalElements;

        /**
         * Nombre total de pages
         */
        private int totalPages;

        /**
         * Indique si c'est la première page
         */
        private boolean first;

        /**
         * Indique si c'est la dernière page
         */
        private boolean last;

        /**
         * Indique si la page est vide (aucun élément)
         */
        private boolean empty;

        /**
         * Indique s'il y a une page suivante
         */
        private boolean hasNext;

        /**
         * Indique s'il y a une page précédente
         */
        private boolean hasPrevious;
    }

    /**
     * Factory method pour créer un PageResponse à partir d'un Page Spring Data.
     *
     * Convertit automatiquement les métadonnées de pagination Spring
     * vers notre format de réponse API.
     *
     * @param page la page Spring Data
     * @param <T> le type d'objet
     * @return une instance PageResponse
     */
    public static <T> PageResponse<T> of(Page<T> page) {
        return PageResponse.<T>builder()
            .content(page.getContent())
            .metadata(PageMetadata.builder()
                .page(page.getNumber())
                .size(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .first(page.isFirst())
                .last(page.isLast())
                .empty(page.isEmpty())
                .hasNext(page.hasNext())
                .hasPrevious(page.hasPrevious())
                .build())
            .build();
    }

    /**
     * Factory method pour créer une page vide.
     *
     * Utile pour retourner une réponse cohérente quand il n'y a aucun résultat.
     *
     * @param <T> le type d'objet
     * @return une instance PageResponse vide
     */
    public static <T> PageResponse<T> empty() {
        return PageResponse.<T>builder()
            .content(List.of())
            .metadata(PageMetadata.builder()
                .page(0)
                .size(0)
                .totalElements(0)
                .totalPages(0)
                .first(true)
                .last(true)
                .empty(true)
                .hasNext(false)
                .hasPrevious(false)
                .build())
            .build();
    }

    /**
     * Factory method pour créer un PageResponse avec une liste simple.
     *
     * Utile pour paginer manuellement une liste déjà chargée en mémoire.
     *
     * @param content la liste d'éléments
     * @param page le numéro de page
     * @param size la taille de page
     * @param totalElements le nombre total d'éléments
     * @param <T> le type d'objet
     * @return une instance PageResponse
     */
    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;

        return PageResponse.<T>builder()
            .content(content)
            .metadata(PageMetadata.builder()
                .page(page)
                .size(size)
                .totalElements(totalElements)
                .totalPages(totalPages)
                .first(page == 0)
                .last(page >= totalPages - 1)
                .empty(content.isEmpty())
                .hasNext(page < totalPages - 1)
                .hasPrevious(page > 0)
                .build())
            .build();
    }
}
