import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, forkJoin, of } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { StudentPaymentStatus, LateGroupDetails } from '../models/student-payment-status';
import { API_BASE_URL } from '../app.config';

/**
 * Service pour gérer le statut de paiement des étudiants
 *
 * Règle métier "En retard":
 * Un étudiant est en retard si il a au moins une session validée à payer
 * et le total dû pour ces sessions est strictement supérieur au total payé.
 */
@Injectable({
  providedIn: 'root'
})
export class StudentPaymentStatusService {

  private readonly baseUrl = `${API_BASE_URL}/api/payments`;

  constructor(private http: HttpClient) {}

  /**
   * Récupère le statut de paiement pour un étudiant
   *
   * @param studentId ID de l'étudiant
   * @returns Observable<StudentPaymentStatus>
   */
  getStudentPaymentStatus(studentId: number): Observable<StudentPaymentStatus> {
    // Utilise l'endpoint existant du backend qui renvoie le statut par groupe
    return this.http.get<any[]>(`${this.baseUrl}/students/${studentId}/payment-status`).pipe(
      map(groupPaymentStatuses => this.transformToStudentPaymentStatus(studentId, groupPaymentStatuses)),
      catchError(error => {
        console.error(`Error fetching payment status for student ${studentId}:`, error);
        // En cas d'erreur, retourner NA (pas de données = pas de statut)
        return of({
          studentId,
          paymentStatus: 'NA',
          lateGroups: [],
          totalDue: 0,
          totalPaid: 0
        } as StudentPaymentStatus);
      })
    );
  }

  /**
   * Récupère le statut de paiement pour plusieurs étudiants en parallèle
   *
   * @param studentIds Liste des IDs d'étudiants
   * @returns Observable<Map<number, StudentPaymentStatus>> Map: studentId -> status
   */
  getMultipleStudentsPaymentStatus(studentIds: number[]): Observable<Map<number, StudentPaymentStatus>> {
    if (studentIds.length === 0) {
      return of(new Map());
    }

    const requests = studentIds.map(id => this.getStudentPaymentStatus(id));

    return forkJoin(requests).pipe(
      map(statuses => {
        const statusMap = new Map<number, StudentPaymentStatus>();
        statuses.forEach(status => {
          statusMap.set(status.studentId, status);
        });
        return statusMap;
      }),
      catchError(error => {
        console.error('Error fetching multiple payment statuses:', error);
        return of(new Map());
      })
    );
  }

  /**
   * Transforme les données du backend (GroupPaymentStatus[]) en StudentPaymentStatus
   *
   * RÈGLE MÉTIER CORRIGÉE (avec filtrage par présence):
   * - Seules les sessions où l'étudiant a une fiche de présence sont comptées
   * - LATE si dueAmountTotal > paidAmountTotal
   * - GOOD si dueAmountTotal <= paidAmountTotal ET dueAmountTotal > 0
   * - NA si dueAmountTotal === 0 (aucune session validée/payable)
   *
   * @private
   */
  private transformToStudentPaymentStatus(
    studentId: number,
    groupPaymentStatuses: any[]
  ): StudentPaymentStatus {
    const lateGroups: LateGroupDetails[] = [];
    let totalDue = 0;
    let totalPaid = 0;
    let totalValidatedSessions = 0;

    // DEBUG: Activer temporairement pour le premier étudiant
    const enableDebug = studentId === 1; // TODO: Retirer après test

    if (enableDebug) {
      console.log(`\n=== DEBUG Payment Status for Student ${studentId} ===`);
    }

    // Pour chaque groupe
    for (const groupStatus of groupPaymentStatuses) {
      const groupId = groupStatus.groupId;
      const groupName = groupStatus.groupName;
      let groupDue = 0;
      let groupPaid = 0;
      let unpaidSessionsCount = 0;
      let groupValidatedSessions = 0;

      // Analyser les séries de sessions du groupe
      for (const seriesStatus of (groupStatus.series || [])) {
        // Analyser les sessions de la série
        for (const sessionStatus of (seriesStatus.sessions || [])) {
          // Le backend ne renvoie QUE les sessions où l'étudiant a une fiche de présence
          // Vérifier si la session est payable
          const isPresent = sessionStatus.isPresent === true;
          const isPaidEvenIfAbsent = sessionStatus.isPaidEvenIfAbsent === true;
          const isPayable = isPresent || isPaidEvenIfAbsent;

          const amountDue = sessionStatus.amountDue || 0;
          const amountPaid = sessionStatus.amountPaid || 0;

          // Si cette session doit être payée
          if (isPayable && amountDue > 0) {
            groupValidatedSessions++;
            groupDue += amountDue;
            groupPaid += amountPaid;

            // Si pas complètement payée, compter comme session impayée
            if (amountPaid < amountDue) {
              unpaidSessionsCount++;
            }

            if (enableDebug) {
              console.log(`  Session ${sessionStatus.sessionId || 'N/A'}: isPresent=${isPresent}, isPaidEvenIfAbsent=${isPaidEvenIfAbsent}, due=${amountDue}, paid=${amountPaid}`);
            }
          }
        }
      }

      if (enableDebug && groupValidatedSessions > 0) {
        console.log(`Group "${groupName}": ${groupValidatedSessions} validated sessions, due=${groupDue}, paid=${groupPaid}`);
      }

      // IMPORTANT: Ajouter aux lateGroups UNIQUEMENT si groupDue > groupPaid
      if (groupDue > groupPaid) {
        lateGroups.push({
          groupId,
          groupName,
          unpaidSessionsCount,
          dueAmount: groupDue,
          paidAmount: groupPaid
        });
      }

      totalDue += groupDue;
      totalPaid += groupPaid;
      totalValidatedSessions += groupValidatedSessions;
    }

    // RÈGLE CORRIGÉE: Déterminer le statut global
    let paymentStatus: 'GOOD' | 'LATE' | 'NA';

    if (totalDue === 0) {
      // Aucune session validée/payable
      paymentStatus = 'NA';
    } else if (totalDue > totalPaid) {
      // Montant dû > montant payé
      paymentStatus = 'LATE';
    } else {
      // Montant dû <= montant payé ET montant dû > 0
      paymentStatus = 'GOOD';
    }

    if (enableDebug) {
      console.log(`TOTAL: ${totalValidatedSessions} validated sessions`);
      console.log(`TOTAL DUE: ${totalDue} DA`);
      console.log(`TOTAL PAID: ${totalPaid} DA`);
      console.log(`STATUS: ${paymentStatus}`);
      console.log(`LATE GROUPS: ${lateGroups.length}`);
      console.log('=== END DEBUG ===\n');
    }

    return {
      studentId,
      paymentStatus,
      lateGroups,
      totalDue,
      totalPaid
    };
  }
}
