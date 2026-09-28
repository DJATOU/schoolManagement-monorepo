import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';

import { AddStudentDialogComponent } from './add-student-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';

/**
 * Ajout d'un étudiant à une séance, par niveau.
 *
 * <p>Deux comportements méritent d'être verrouillés : les étudiants déjà présents sont
 * **écartés** de la liste — les proposer permettrait de les ajouter deux fois — et l'ordre
 * est alphabétique, celui de l'API étant arbitraire et donc inutilisable pour chercher un nom
 * à l'œil.</p>
 */
describe('AddStudentDialogComponent', () => {
  let component: AddStudentDialogComponent;
  let fixture: ComponentFixture<AddStudentDialogComponent>;
  let httpMock: HttpTestingController;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(AddStudentDialogComponent, {
      providers: matDialogProviders({ levelId: 3, existingStudentIds: [2] }, dialogRef)
    });
    fixture = TestBed.createComponent(AddStudentDialogComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Répond au chargement des étudiants du niveau déclenché par `ngOnInit`. */
  function flushStudents(): void {
    httpMock.expectOne(req => req.url.includes('/level/') || req.url.includes('level'))
      .flush([
        aStudent({ id: 3, firstName: 'Zineb', lastName: 'Zerrouki' }),
        aStudent({ id: 2, firstName: 'Bilal', lastName: 'Bensalem' }),
        aStudent({ id: 1, firstName: 'Amina', lastName: 'Belkacem' })
      ]);
    fixture.detectChanges();
  }

  it('should create', () => {
    flushStudents();
    expect(component).toBeTruthy();
  });

  it('écarte les étudiants déjà présents dans la séance', () => {
    flushStudents();

    // L'étudiant 2 est annoncé comme déjà présent : le proposer permettrait de l'ajouter
    // une seconde fois à la même séance.
    expect(component.students.map(student => student.id)).not.toContain(2);
  });

  it('trie les étudiants par nom puis prénom', () => {
    flushStudents();

    expect(component.students.map(student => student.lastName))
      .toEqual(['Belkacem', 'Zerrouki']);
  });
});
