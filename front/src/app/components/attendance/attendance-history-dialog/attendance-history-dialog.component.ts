import { Component, Inject, OnInit } from '@angular/core';
import { Attendance } from '../../../models/Attendance/attendance';
import { Group } from '../../../models/group/group';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { AttendanceService } from '../../../services/attendance.service';
import { SeriesService } from '../../../services/series.service';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialog, MatDialogModule, MatDialogRef } from '@angular/material/dialog';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { MatOptionModule } from '@angular/material/core';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { StudentService } from '../../student/services/student.service';
import { SessionService } from '../../../services/SessionService';
import { MatIconModule } from '@angular/material/icon';
import { MatTooltipModule } from '@angular/material/tooltip';
import { JustificationUpdateResult } from '../../../models/Attendance/justification';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import {
  JustificationEditDialogComponent
} from '../justification-edit-dialog/justification-edit-dialog.component';

import pdfMake from 'pdfmake/build/pdfmake';
import pdfFonts from 'pdfmake/build/vfs_fonts';

(pdfMake as any).vfs = pdfFonts.pdfMake.vfs;

import { Content, TDocumentDefinitions } from 'pdfmake/interfaces';

@Component({
  selector: 'app-attendance-history-dialog',
  standalone: true,
  templateUrl: './attendance-history-dialog.component.html',
  styleUrls: ['./attendance-history-dialog.component.scss'],
  imports: [
    CommonModule,
    FormsModule,
    MatDialogModule,
    MatButtonModule,
    MatFormFieldModule,
    MatSelectModule,
    MatOptionModule,
    MatTableModule,
    MatIconModule,
    MatTooltipModule,
    AdminOnlyDirective
  ]
})
export class AttendanceHistoryDialogComponent implements OnInit {
  studentGroups: Group[] = [];
  sessionSeries: SessionSeries[] = [];
  attendanceHistory = new MatTableDataSource<any>(); // Utiliser any pour inclure les données supplémentaires

  selectedGroup: number | null = null;
  selectedSeries: number | null = null;

  displayedColumns: string[] = ['session', 'attendanceDate', 'isPresent', 'isJustified',
    'description', 'actions'];

  studentName: string = '';

  constructor(
    private attendanceService: AttendanceService,
    private studentService: StudentService,
    private seriesService: SeriesService,
    private sessionService: SessionService,
    private dialog: MatDialog,
    public dialogRef: MatDialogRef<AttendanceHistoryDialogComponent>,
    @Inject(MAT_DIALOG_DATA) public data: { studentId: number }
  ) {}

  ngOnInit(): void {
    this.loadStudentInfo();
    this.loadGroups();
  }

  private loadStudentInfo(): void {
    this.studentService.getStudentById(this.data.studentId).subscribe({
      next: (student) => {
        this.studentName = `${student.firstName} ${student.lastName}`;
      },
      error: (error) => {
        console.error('Error loading student info:', error);
      }
    });
  }

  private loadGroups(): void {
    this.studentService.getGroupsForStudent(this.data.studentId).subscribe({
      next: (groups) => {
        this.studentGroups = groups;
      },
      error: (error) => {
        console.error('Error loading groups:', error);
      }
    });
  }

  loadSessionSeries(): void {
    if (this.selectedGroup) {
      this.seriesService.getSessionSeriesByGroupId(this.selectedGroup).subscribe({
        next: (series) => {
          this.sessionSeries = series;
          this.selectedSeries = null;
          this.attendanceHistory.data = [];
        },
        error: (error) => {
          console.error('Error loading session series:', error);
        }
      });
    }
  }

  loadAttendanceHistory(): void {
    if (this.selectedSeries && this.selectedGroup) {
      this.attendanceService.getAttendanceByStudentAndSeries(this.data.studentId, this.selectedSeries).subscribe({
        next: (attendanceRecords) => {
          // Pour chaque enregistrement de présence, récupérer les détails de la session
          const sessionRequests = attendanceRecords.map(attendance =>
            this.sessionService.getSessionById(attendance.sessionId).toPromise()
          );

          Promise.all(sessionRequests).then(sessions => {
            this.attendanceHistory.data = attendanceRecords.map((attendance, index) => {
              const session = sessions[index];
              return {
                ...attendance,
                sessionName: session?.title || 'Session inconnue', // Récupérer le nom de la session ou une valeur par défaut
                sessionDate: session?.sessionTimeStart || null // Récupérer la date de la session ou null
              };
            });
          }).catch(error => {
            console.error('Error loading session data:', error);
          });
        },
        error: (error: Error) => {
          console.error('Error loading attendance history:', error);
        }
      });
    } else {
      console.error('Selected series or group is null or undefined.');
    }
  }

  getRowClass(attendance: Attendance): string {
    if (attendance.isPresent) {
      return 'row-present';
    } else if (attendance.isJustified) {
      return 'row-justified';
    } else {
      return 'row-not-justified';
    }
  }

  /**
   * Libellé de la justification d'une absence.
   *
   * <p>Trois valeurs distinctes et non deux : « non renseignée » n'est pas « non justifiée ». Afficher
   * un `null` comme un « Non » affirmerait une décision que personne n'a prise.</p>
   *
   * <p>Le libellé est textuel, et pas seulement porté par la couleur de ligne : celle-ci ne survit ni
   * à une impression en noir et blanc ni au daltonisme.</p>
   */
  justificationLabel(attendance: Attendance): string {
    if (attendance.isPresent) {
      return '';
    }
    if (attendance.isJustified === null || attendance.isJustified === undefined) {
      return 'Non renseignée';
    }
    return attendance.isJustified ? 'Justifiée' : 'Non justifiée';
  }

  /** Vrai si la justification de cette ligne est modifiable : seules les absences le sont. */
  canEditJustification(attendance: Attendance): boolean {
    return !attendance.isPresent && attendance.id != null;
  }

  /**
   * Ouvre le dialogue de modification de la justification.
   *
   * <p>Le dialogue affiche lui-même que la modification ne change aucun montant, et charge la piste
   * d'audit : c'est là que l'information arrive à temps, au moment du geste.</p>
   */
  editJustification(attendance: any): void {
    if (!this.canEditJustification(attendance)) {
      return;
    }

    this.dialog.open(JustificationEditDialogComponent, {
      width: '520px',
      maxWidth: '95vw',
      autoFocus: false,
      data: {
        attendanceId: attendance.id,
        justified: attendance.isJustified ?? null,
        sessionName: attendance.sessionName,
        sessionDate: attendance.sessionDate
      }
    }).afterClosed().subscribe((result?: JustificationUpdateResult) => {
      if (!result) {
        return;
      }
      // Mise à jour en place plutôt que rechargement complet : la justification n'affecte aucun
      // montant, il n'y a donc rien d'autre à rafraîchir.
      attendance.isJustified = result.justified;
    });
  }

  // Méthode pour convertir l'image en Base64
  private convertImageToBase64(url: string): Promise<string> {
    return new Promise((resolve, reject) => {
      const img = new Image();
      img.crossOrigin = 'Anonymous';
      img.src = url;
      img.onload = () => {
        const canvas = document.createElement('canvas');
        canvas.width = img.width;
        canvas.height = img.height;
        const ctx = canvas.getContext('2d');
        ctx?.drawImage(img, 0, 0);
        const dataURL = canvas.toDataURL('image/png');
        resolve(dataURL);
      };
      img.onerror = error => {
        reject(error);
      };
    });
  }

  // Méthode pour générer le PDF
  async generatePdf(): Promise<void> {
    let logoBase64 = '';
    try {
      logoBase64 = await this.convertImageToBase64('assets/succes_assistance.png');
    } catch (error) {
      console.error('Erreur lors du chargement du logo :', error);
    }

    const documentDefinition: TDocumentDefinitions = {
      content: [
        {
          columns: [
            {
              image: logoBase64,
              width: 100
            },
            {
              text: 'Historique des Présences',
              style: 'header',
              alignment: 'right'
            }
          ]
        },
        { text: '\n\n' },
        {
          text: `Étudiant : ${this.studentName}`,
          style: 'subheader'
        },
        {
          text: `Date : ${new Date().toLocaleDateString()}`,
          alignment: 'right'
        },
        { text: '\n' },
        {
          text: `${this.sessionSeries.find(series => series.id === this.selectedSeries)?.name}`,
          style: 'sectionHeader'
        },
        { text: '\n' },
        this.getAttendanceTable(),
        { text: '\n\n' },
        {
          columns: [
            {
              text: 'Signature de l\'Étudiant : ________________________',
              alignment: 'left',
              margin: [0, 50, 0, 0]
            },
            {
              text: 'Signature de l\'Administration : ________________________',
              alignment: 'right',
              margin: [0, 50, 0, 0]
            }
          ]
        }
      ],
      styles: {
        header: {
          fontSize: 22,
          bold: true,
          color: '#2F5496',
          margin: [0, 0, 0, 10]
        },
        subheader: {
          fontSize: 16,
          bold: true,
          margin: [0, 10, 0, 5]
        },
        sectionHeader: {
          fontSize: 18,
          bold: true,
          color: '#2F5496',
          margin: [0, 15, 0, 10]
        },
        tableHeader: {
          bold: true,
          fontSize: 12,
          color: 'white',
          fillColor: '#4F81BD',
          alignment: 'center'
        },
        tableCell: {
          margin: [0, 5, 0, 5]
        }
      },
      footer: (currentPage: number, pageCount: number): Content => {
        return {
          text: `Page ${currentPage} sur ${pageCount}`,
          alignment: 'center',
          fontSize: 10,
          margin: [0, 10, 0, 0]
        } as Content;
      }
    };

    const pdfDocGenerator = pdfMake.createPdf(documentDefinition);

    pdfDocGenerator.getBlob((blob) => {
      const blobUrl = URL.createObjectURL(blob);
      window.open(blobUrl, '_blank');
    });
  }

  private getAttendanceTable(): any {
    const body = [];

    // En-têtes du tableau
    body.push([
      { text: 'Session', style: 'tableHeader' },
      { text: 'Date de la Session', style: 'tableHeader' },
      { text: 'Présence', style: 'tableHeader' },
      { text: 'Justifiée', style: 'tableHeader' },
      { text: 'Description', style: 'tableHeader' }
    ]);

    // Données du tableau
    if (this.attendanceHistory.data && this.attendanceHistory.data.length > 0) {
      for (const attendance of this.attendanceHistory.data) {
        const fillColor = this.getFillColorForAttendance(attendance);
        const sessionName = attendance.sessionName || 'N/A';
        const sessionDisplayName = attendance.isCatchUp ? `${sessionName} (Rattrapage)` : sessionName;

        const justifiedText = attendance.isPresent ? '' : (attendance.isJustified ? 'Oui' : 'Non');

        body.push([
          { text: sessionDisplayName, fillColor, ...(attendance.isCatchUp && { color: 'red', bold: true }) },
          { text: attendance.sessionDate ? new Date(attendance.sessionDate).toLocaleDateString() : 'N/A', fillColor },
          { text: attendance.isPresent ? 'Oui' : 'Non', fillColor },
          { text: justifiedText, fillColor },
          { text: attendance.description || '', fillColor }
        ]);
      }
    } else {
      body.push([
        { text: 'Aucune donnée disponible', colSpan: 5, alignment: 'center' }
      ]);
    }

    return {
      table: {
        headerRows: 1,
        widths: ['*', '*', '*', '*', '*'],
        body: body
      },
      layout: 'lightHorizontalLines'
    };
  }

  private getFillColorForAttendance(attendance: Attendance): string {
    if (attendance.isPresent) {
      return '#d4edda'; // Vert pour présent
    } else if (attendance.isJustified) {
      return '#ffeeba'; // Orange pour absent justifié
    } else {
      return '#f8d7da'; // Rouge pour absent non justifié
    }
  }
}
