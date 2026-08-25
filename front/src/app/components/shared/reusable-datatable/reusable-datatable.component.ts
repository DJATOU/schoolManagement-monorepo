import { Component, Input, OnInit, Optional, ViewChild } from '@angular/core';
import { MatRecycleRows, MatTableDataSource, MatTableModule} from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { SelectionModel } from '@angular/cdk/collections';
import { DatePipe, NgIf } from '@angular/common';
import { MatIcon } from '@angular/material/icon';
import { MatButton } from '@angular/material/button';
import { Router } from '@angular/router';
import { Observable } from 'rxjs';
import { SummaryDialogComponent } from '../../summary-dialog/summary-dialog.component';
import { MatDialog } from '@angular/material/dialog';
import { DeleteCommand } from './DeleteCommand';
import { MatSnackBar } from '@angular/material/snack-bar';
import { ConfirmationDialogComponent } from '../confirmation-dialog/confirmation-dialog.component';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { ProfilePdfService } from '../../../services/profile-pdf.service';
import { MatTooltipModule } from '@angular/material/tooltip';
import { AdminOnlyDirective } from '../../../shared/admin-only.directive';
import { resolveLocale } from '../../../shared/locale';
import { parseJavaDate } from '../../../shared/java-date';

interface ColumnDefenition {
  columnDef: string;
  header: string;
  cell: (element: any) => string;
}

@Component({
  selector: 'app-reusable-datatable',
  standalone: true,
  imports: [NgIf, MatIcon, MatButton, MatTableModule, MatPaginatorModule, MatSortModule,
    MatFormFieldModule, MatInputModule, MatCheckboxModule, MatRecycleRows, MatTooltipModule,
    TranslateModule, AdminOnlyDirective],
  templateUrl: './reusable-datatable.component.html',
  styleUrl: './reusable-datatable.component.scss'
})
export class ReusableDatatableComponent  implements OnInit{
  @Input() columns!: ColumnDefenition[];
  @Input() observable!: Observable<any[]>;
  @Input() dataType!: string;
  @Input() deleteCommand!: DeleteCommand;

  dataSource!: MatTableDataSource<any>;
  displayedColumns: string[] = [];
  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;
  selection = new SelectionModel<any>(true, []);
  data: any;
  datePipe: DatePipe;

  ngOnInit(): void {
    console.log('Received columns:', this.columns);
    this.displayedColumns = ['select', ...this.columns.map(c => c.columnDef)];
    
    this.observable.subscribe((data: any[]) => {
      console.log('Received data:', data);
      this.dataSource = new MatTableDataSource<any>(data);
      this.dataSource.paginator = this.paginator;
      this.dataSource.sort = this.sort;
      this.data = data;
    });
  }

  constructor(private router: Router,
              public dialog: MatDialog,
              private snackBar: MatSnackBar,
              private translate: TranslateService,
              private profilePdfService: ProfilePdfService) {
    this.datePipe = new DatePipe(resolveLocale(this.translate.currentLang));
    this.translate.onLangChange.subscribe(event => {
      this.datePipe = new DatePipe(resolveLocale(event.lang));
    });
  }
  
  /** Implement create logic */
  onCreate() {
    this.router.navigate([this.dataType+'/new/']);
  }

  /** Implement view logic */
  onView() {
    this.selection.selected.forEach((selected) => {
      let informations= this.flattenFormData(selected, this.columns , this.translate.instant('DATATABLE.PRIMARY_INFORMATION'));

      let otherColumn= Object.keys(selected).filter(key => !this.columns.some(column => column.columnDef === key));
      let otherInformation= this.flattenFormData(selected, otherColumn , this.translate.instant('DATATABLE.OTHER_INFORMATION'))
      
      informations.push(...otherInformation);

      this.dialog.open(SummaryDialogComponent, {
        data: informations
      });
    });
  }

  /** This function is used like an Adapter that transform simple object to summary-dialog input*/
  flattenFormData(data: any,cols: any[], parentKey: string = this.translate.instant('DATATABLE.INFORMATION')): { label: string, value: any }[] {
    let result: { label: string, value: any }[] = [];

    // Ajouter les informations fondamentales en premiers
    cols.forEach((column) => {
      const key = column.columnDef || column;
      const newKey = parentKey ? `${parentKey} - ${key}` : key;
      let value = data[key];

      if( (key=="dateCreation" || key=="dateUpdate") && value){
        value= this.convertDate(value);
      }

      if (typeof value === 'object' && value !== null && !Array.isArray(value)) {
        // Recursively flatten the nested object
        result = result.concat(this.flattenFormData(value, newKey));
      } else if (Array.isArray(value)) {
        // Convert array to string
        result.push({ label: newKey, value: value.join(', ') });
      } else {
        result.push({ label: newKey, value: value });
      }
    });
    return result;
  }

  /**
   * Formate une date issue de l'API pour le dialogue « Voir ».
   *
   * <p>La conversion précédente appelait {@code date.toString().split(',')} sans garde :
   * une date nulle levait une TypeError. Elle ne gérait par ailleurs que le format tableau
   * de Jackson, pas les chaînes ISO.</p>
   */
  convertDate(date: unknown): string {
    const parsed = parseJavaDate(date);
    return parsed ? (this.datePipe.transform(parsed, 'dd MMMM yyyy') ?? '') : '';
  }
  
  /** Implement edit logic */
  onEdit() {
    this.router.navigate([this.dataType+'/edit/'+this.selection.selected[0].id]);
  }
  
  /** Implement delete logic */
  onDisable() {
    if( this.selection.selected.length != 0){
      //Faire la confirmation avant la suppression
      this.dialog.open(ConfirmationDialogComponent, {
        data:{
          title: this.translate.instant('CONFIRMATION_DIALOG.TITLE'),
          message: this.translate.instant('CONFIRMATION_DIALOG.MESSAGE',
            { count: this.selection.selected.length }),
          confirmText: this.translate.instant('CONFIRMATION_DIALOG.CONFIRM'),
          cancelText: this.translate.instant('CONFIRMATION_DIALOG.CANCEL'),
          confirmColor: 'warn'
        }
      }).afterClosed().subscribe((result: boolean) => {
        if (result) {
          let id_list = this.selection.selected.map((selected) => Number(selected.id));
          this.deleteCommand.disableItems(id_list).subscribe({
            next: (response) => {
              //Mise à jour du tableau
              this.data = this.data.filter((data: any) => !this.selection.selected.includes(data));
              this.dataSource = new MatTableDataSource<any>(this.data);
              this.dataSource.paginator = this.paginator;
              this.dataSource.sort = this.sort;
              this.selection.clear();

              console.log('Elements disabled successfully:', response);
              this.showSuccessMessage(this.translate.instant('DATATABLE.DISABLE_SUCCESS'));
            },
            error: (error) => {
              console.error('Error disabling elements:', error);
              this.showErrorMessage(this.translate.instant('DATATABLE.DISABLE_ERROR'));
            }
          });
        }
        else{
          console.log('Operation canceled.');
        }
      });
    }
  }
  
  showSuccessMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-success']
    });
  }

  showErrorMessage(message: string): void {
    this.snackBar.open(message, 'OK', {
      duration: 3000,
      panelClass: ['snack-bar-error']
    });
  }
  
  /**
   * Génère un PDF de la liste.
   *
   * <p>Respecte le contexte de l'écran : si des lignes sont sélectionnées, seule la
   * sélection est imprimée ; sinon on imprime les lignes <strong>filtrées</strong>
   * (résultat de la recherche) et non l'intégralité des données.</p>
   */
  onPrint() {
    const rows = this.rowsToPrint();
    if (rows.length === 0) {
      this.showErrorMessage(this.translate.instant('DATATABLE.PRINT_EMPTY'));
      return;
    }

    const onSelection = this.selection.hasValue();
    const subtitle = this.translate.instant(
      onSelection ? 'DATATABLE.PRINT_SUBTITLE_SELECTION' : 'DATATABLE.PRINT_SUBTITLE_ALL',
      { count: rows.length });

    this.profilePdfService.generateProfilePdf({
      title: this.entityTitle,
      subtitle,
      sections: [],
      tableTitle: this.entityTitle,
      tableColumns: this.columns.map(c => c.header),
      tableRows: rows.map(row => this.columns.map(c => this.display(c.cell(row))))
    });
  }

  /** Lignes à imprimer : la sélection si elle existe, sinon les lignes filtrées. */
  private rowsToPrint(): any[] {
    if (this.selection.hasValue()) {
      return this.selection.selected;
    }
    return this.dataSource?.filteredData ?? [];
  }

  /** Infobulle du bouton d'impression, selon qu'une sélection est active ou non. */
  get printTooltip(): string {
    return this.selection.hasValue()
      ? this.translate.instant('DATATABLE.PRINT_SELECTION', { count: this.selection.selected.length })
      : this.translate.instant('DATATABLE.PRINT_ALL');
  }

  /** Titre de la page, dérivé du type d'entité (repli sur le type brut). */
  get entityTitle(): string {
    const key = `DATATABLE.ENTITIES.${this.dataType}.title`;
    const label = this.translate.instant(key);
    return label === key ? this.dataType : label;
  }

  /** Sous-titre explicatif de la page. */
  get entitySubtitle(): string {
    const key = `DATATABLE.ENTITIES.${this.dataType}.subtitle`;
    const label = this.translate.instant(key);
    return label === key ? '' : label;
  }

  /** Icône illustrant l'entité listée. */
  get entityIcon(): string {
    switch (this.dataType) {
      case 'level': return 'stairs';
      case 'room': return 'meeting_room';
      case 'subject': return 'menu_book';
      case 'groupType': return 'category';
      case 'pricing': return 'price_change';
      default: return 'table_rows';
    }
  }

  /** Nombre de lignes actuellement affichées (après filtrage). */
  get rowCount(): number {
    return this.dataSource?.filteredData?.length ?? 0;
  }

  /** Vrai si des données existent (permet de distinguer « vide » de « aucun résultat »). */
  get hasData(): boolean {
    return (this.data?.length ?? 0) > 0;
  }

  /**
   * Nettoie une valeur de cellule : les fabriques `cell()` interpolent des champs absents,
   * ce qui produit les chaînes « undefined » / « null » à l'écran comme dans le PDF.
   */
  display(value: any): string {
    if (value === null || value === undefined) {
      return '';
    }
    const text = String(value).trim();
    return text === 'undefined' || text === 'null' ? '' : text;
  }

  /**For the filter option. */
  applyFilter(event: Event) {
    const filterValue = (event.target as HTMLInputElement).value;
    this.dataSource.filter = filterValue.trim().toLowerCase();
  }

  
  /** This part is for selection */
  /** Whether the number of selected elements matches the total number of rows. */
  isAllSelected() {
    const numSelected = this.selection.selected.length;
    const numRows = this.dataSource?.data.length || 0;
    return numSelected === numRows;
  }

  /** Selects all rows if they are not all selected; otherwise clear selection. */
  toggleAllRows() {
    if (this.isAllSelected()) {
      this.selection.clear();
      return;
    }

    this.selection.select(...this.dataSource.data);
  }

  /** The label for the checkbox on the passed row */
  checkboxLabel(row?: any): string {
    if (!row) {
      return `${this.isAllSelected() ? 'deselect' : 'select'} all`;
    }
    return `${this.selection.isSelected(row) ? 'deselect' : 'select'} row ${row.id! + 1}`;
  }

  /** True if one item is selected*/
  isOneItemSelected() {
    return this.selection.selected.length === 1;
  }
}

