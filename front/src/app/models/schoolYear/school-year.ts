// src/app/models/schoolYear/school-year.ts

/**
 * Modèle Année Scolaire (School Year)
 *
 * Correspond au SchoolYearDTO du backend.
 * @see SchoolYearController.java (backend) - /api/school-years
 */
export interface SchoolYear {
  id?: number;
  label: string;        // Format "YYYY-YYYY", ex: "2025-2026"
  startDate: string;    // Format "yyyy-MM-dd"
  endDate: string;      // Format "yyyy-MM-dd"
  isCurrent?: boolean;  // Une seule année scolaire courante à la fois
}
