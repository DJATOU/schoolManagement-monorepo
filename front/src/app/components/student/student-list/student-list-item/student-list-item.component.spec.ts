import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { StudentListItemComponent } from './student-list-item.component';
import { setupComponentTestBed } from '../../../../../testing/setup';
import { aPaymentStatus, aStudent } from '../../../../../testing/fixtures';

/**
 * `student` est une entrée obligatoire, déréférencée par `ngOnInit`.
 *
 * <p>Le statut de paiement est fourni par le parent dans la liste : le composant ne
 * l'interroge lui-même qu'à défaut. Les tests couvrent les deux chemins, celui du parent
 * étant le seul emprunté en production par la liste des étudiants.</p>
 */
describe('StudentListItemComponent', () => {
  let component: StudentListItemComponent;
  let fixture: ComponentFixture<StudentListItemComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(StudentListItemComponent);
    fixture = TestBed.createComponent(StudentListItemComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('student', aStudent());
    fixture.componentRef.setInput('paymentStatus', aPaymentStatus());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le nom de l\'étudiant', () => {
    expect(fixture.nativeElement.textContent).toContain('Amina');
    expect(fixture.nativeElement.textContent).toContain('Belkacem');
  });

  it('compose les initiales à partir du prénom et du nom', () => {
    expect(component.getInitials()).toBe('AB');
  });

  it('masque le bouton de suppression par défaut', () => {
    // La liste principale ne propose pas la suppression ; seul l'écran de groupe l'active.
    expect(component.showDeleteButton).toBeFalse();
  });

  it('émet la demande de suppression sans propager le clic', () => {
    // Sans stopPropagation, supprimer un étudiant naviguerait aussi vers sa fiche.
    const event = new MouseEvent('click');
    const stopPropagation = spyOn(event, 'stopPropagation');
    let emitted: unknown = null;
    component.deleteStudent.subscribe(student => (emitted = student));

    component.onDeleteStudent(event);

    expect(emitted).toEqual(component.student);
    expect(stopPropagation).toHaveBeenCalled();
  });

  it('navigue vers la fiche de l\'étudiant', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');

    component.navigateToStudent(component.student);

    expect(navigate).toHaveBeenCalledWith(['/student', 1]);
  });

  it('associe une icône au statut de paiement fourni par le parent', () => {
    expect(component.getPaymentIcon()).toBe('check_circle');

    fixture.componentRef.setInput('paymentStatus', aPaymentStatus({ paymentStatus: 'LATE' }));
    expect(component.getPaymentIcon()).toBe('warning');
  });

  it('n\'affiche aucune icône en l\'absence de statut', () => {
    const withoutStatus = TestBed.createComponent(StudentListItemComponent);
    // Étudiant sans identifiant : le composant n'a alors aucun statut à charger de lui-même.
    withoutStatus.componentRef.setInput('student', aStudent({ id: undefined }));
    withoutStatus.detectChanges();

    expect(withoutStatus.componentInstance.getPaymentIcon()).toBe('');
  });
});
