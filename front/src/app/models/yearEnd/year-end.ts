// src/app/models/yearEnd/year-end.ts

/**
 * Modèles du workflow de fin d'année (Year_End_Workflow).
 *
 * Correspondent aux DTOs backend du package com.school.management.dto :
 * - YearEndPreviewDTO / StudentDecisionPreviewDTO
 * - YearEndRequestDTO / StudentDecisionDTO
 * - YearEndResultDTO
 *
 * @see YearEndWorkflowController.java (backend) - /api/year-end
 */

import { SchoolYear } from '../schoolYear/school-year';

/**
 * Décision de promotion appliquée à un étudiant en fin d'année.
 * Correspond à l'énumération PromotionDecision du backend.
 */
export enum PromotionDecision {
  PROMOTION = 'PROMOTION',
  REDOUBLEMENT = 'REDOUBLEMENT',
  DEPARTURE = 'DEPARTURE'
}

/**
 * Statut d'inscription d'un étudiant (StudentStatus backend).
 */
export enum StudentStatus {
  ACTIVE = 'ACTIVE',
  INACTIVE = 'INACTIVE'
}

/**
 * Vue minimale d'un étudiant telle que renvoyée par le backend (StudentDTO).
 * Seuls les champs utiles au workflow de fin d'année sont typés ici.
 */
export interface StudentSummary {
  id?: number;
  firstName?: string;
  lastName?: string;
  levelId?: number;
  status?: StudentStatus;
  [key: string]: unknown;
}

/**
 * Décision par défaut proposée pour un étudiant (StudentDecisionPreviewDTO).
 */
export interface StudentDecisionPreview {
  student: StudentSummary;
  decision: PromotionDecision;
  needsReview: boolean;
}

/**
 * Aperçu du workflow de fin d'année (YearEndPreviewDTO).
 */
export interface YearEndPreview {
  proposedNextLabel: string;
  decisions: StudentDecisionPreview[];
}

/**
 * Décision de fin d'année pour un étudiant donné (StudentDecisionDTO).
 */
export interface StudentDecision {
  studentId: number;
  decision: PromotionDecision;
}

/**
 * Requête d'exécution du workflow de fin d'année (YearEndRequestDTO).
 * Les champs de libellé/dates sont optionnels (dérivés côté backend si absents).
 */
export interface YearEndRequest {
  newLabel?: string;
  startDate?: string; // Format "yyyy-MM-dd"
  endDate?: string;   // Format "yyyy-MM-dd"
  decisions: StudentDecision[];
}

/**
 * Résultat du workflow de fin d'année (YearEndResultDTO).
 */
export interface YearEndResult {
  newYear: SchoolYear;
  reviewList: StudentSummary[];
  appliedCount: number;
}
