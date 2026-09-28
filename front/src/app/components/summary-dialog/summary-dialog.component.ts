import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { CommonModule } from '@angular/common';
import { MatButtonModule } from '@angular/material/button';
import { MatDialogModule } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { TranslateModule, TranslateService } from '@ngx-translate/core';

@Component({
  selector: 'app-summary-dialog',
  templateUrl: './summary-dialog.component.html',
  styleUrls: ['./summary-dialog.component.scss'],
  standalone: true,
  imports: [CommonModule, MatDialogModule, MatButtonModule, MatIconModule, TranslateModule]
})
export class SummaryDialogComponent {
  sections: { title: string; fields: { label: string; value: any }[] }[] = [];

  /** Champs à ne jamais afficher dans le résumé (techniques / non pertinents). */
  private readonly hiddenFields = ['photo', 'file'];

  constructor(
    public dialogRef: MatDialogRef<SummaryDialogComponent>,
    private translate: TranslateService,
    @Inject(MAT_DIALOG_DATA) public data: any
  ) {
    const groupedData: { [key: string]: { label: string; value: any }[] } = {};

    data.forEach((item: { label: string; value: any }) => {
      const [section, ...rest] = item.label.split(' - ');
      const field = rest.join(' - ');

      // Ignorer les champs techniques et les valeurs vides
      if (this.hiddenFields.includes(field)) {
        return;
      }
      if (item.value === null || item.value === undefined || String(item.value).trim() === '') {
        return;
      }

      if (!groupedData[section]) {
        groupedData[section] = [];
      }
      groupedData[section].push({ label: field, value: item.value });
    });

    this.sections = Object.keys(groupedData)
      .map(section => ({
        title: section,
        fields: groupedData[section]
      }))
      // Ne pas afficher une section devenue vide après filtrage
      .filter(section => section.fields.length > 0);
  }

  /**
   * Traduit un intitulé de section. Cherche d'abord une clé de traduction
   * dédiée (SUMMARY.SECTIONS.<clé>), sinon retombe sur le libellé camelCase.
   */
  translateSection(key: string): string {
    return this.translateWithFallback(`SUMMARY.SECTIONS.${key}`, key);
  }

  /** Traduit un intitulé de champ (SUMMARY.FIELDS.<clé>), avec repli camelCase. */
  translateField(key: string): string {
    return this.translateWithFallback(`SUMMARY.FIELDS.${key}`, key);
  }

  private translateWithFallback(translationKey: string, rawKey: string): string {
    const translated = this.translate.instant(translationKey);
    // ngx-translate renvoie la clé elle-même si aucune traduction n'existe
    if (translated && translated !== translationKey) {
      return translated;
    }
    // Repli : transforme le camelCase en texte lisible
    return rawKey
      .replace(/([A-Z])/g, ' $1')
      .replace(/^./, c => c.toUpperCase())
      .trim();
  }

  onConfirm(): void {
    this.dialogRef.close(true);
  }

  onCancel(): void {
    this.dialogRef.close(false);
  }
}
