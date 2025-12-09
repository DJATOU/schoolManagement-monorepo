package com.school.management.infrastructure.config.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Configuration de la pagination globale pour l'application.
 *
 * Définit les paramètres par défaut pour la pagination Spring Data:
 * - Taille de page par défaut: 20 éléments
 * - Taille de page maximale: 100 éléments
 * - Paramètres de requête: page, size, sort
 * - Index de page: commence à 0
 *
 * Usage dans les controllers:
 * <pre>
 * @GetMapping
 * public ResponseEntity<PageResponse<StudentDTO>> getAll(
 *     @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {
 *     Page<Student> students = studentService.findAll(pageable);
 *     return ResponseEntity.ok(PageResponse.of(students.map(mapper::toDTO)));
 * }
 * </pre>
 *
 * Exemples d'appels API:
 * - GET /api/students?page=0&size=20
 * - GET /api/students?page=1&size=50&sort=lastName,asc
 * - GET /api/students?page=0&size=10&sort=dateOfBirth,desc
 *
 * @author Claude Code
 * @since Phase 2 Refactoring
 */
@Configuration
@EnableSpringDataWebSupport
public class PaginationConfig implements WebMvcConfigurer {

    /**
     * Configure le resolver de pagination pour les controllers.
     *
     * Paramètres configurés:
     * - fallbackPageable: Page 0, 20 éléments par défaut
     * - maxPageSize: Maximum 100 éléments par page
     * - pageParameterName: "page" dans l'URL
     * - sizeParameterName: "size" dans l'URL
     * - oneIndexedParameters: false (commence à 0, pas à 1)
     */
    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        PageableHandlerMethodArgumentResolver resolver = new PageableHandlerMethodArgumentResolver();

        // Pagination par défaut si non spécifiée
        resolver.setFallbackPageable(
            org.springframework.data.domain.PageRequest.of(0, 20)
        );

        // Limite maximale de taille de page (évite les requêtes trop larges)
        resolver.setMaxPageSize(100);

        // Noms des paramètres de requête
        resolver.setPageParameterName("page");
        resolver.setSizeParameterName("size");

        // Index de page commence à 0 (standard REST)
        resolver.setOneIndexedParameters(false);

        // Préfixe pour les paramètres de tri (ex: sort=lastName,asc)
        resolver.setPrefix("");
        resolver.setQualifierDelimiter("_");

        resolvers.add(resolver);
    }
}
