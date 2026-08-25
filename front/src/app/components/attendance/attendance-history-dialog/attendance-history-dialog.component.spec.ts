import { ComponentFixture, TestBed } from '@angular/core/testing';

import { AttendanceHistoryDialogComponent } from './attendance-history-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';

/**
 * Historique d'assiduité d'un étudiant.
 *
 * <p>`data.studentId` est déréférencé dès `ngOnInit`, qui déclenche deux chargements. Les
 * requêtes sont laissées en attente : ce qui est vérifié ici est que le dialogue se construit
 * et s'ouvre sur l'étudiant demandé, pas le contenu de l'historique.</p>
 */
describe('AttendanceHistoryDialogComponent', () => {
  let component: AttendanceHistoryDialogComponent;
  let fixture: ComponentFixture<AttendanceHistoryDialogComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(AttendanceHistoryDialogComponent, {
      providers: matDialogProviders({ studentId: 42 }, dialogRef)
    });
    fixture = TestBed.createComponent(AttendanceHistoryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte l\'étudiant sur lequel il a été ouvert', () => {
    expect(component.data.studentId).toBe(42);
  });

  it('n\'affiche aucun nom avant la réponse du serveur', () => {
    // Le nom est chargé de façon asynchrone : afficher « undefined undefined » en attendant
    // serait pire que de n'afficher rien.
    expect(component.studentName).toBe('');
  });
});
