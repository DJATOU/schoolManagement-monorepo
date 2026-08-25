import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { ProfileCardComponent } from './profile-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aPaymentStatus, aProfile } from '../../../../testing/fixtures';

/**
 * `profile` et `profileType` sont des entrées obligatoires que `ngOnInit` déréférence
 * aussitôt : la carte ne peut pas être construite sans elles.
 */
describe('ProfileCardComponent', () => {
  let component: ProfileCardComponent;
  let fixture: ComponentFixture<ProfileCardComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(ProfileCardComponent);
    fixture = TestBed.createComponent(ProfileCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('profile', aProfile());
    fixture.componentRef.setInput('profileType', 'student');
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le nom du profil', () => {
    expect(fixture.nativeElement.textContent).toContain('Amina');
    expect(fixture.nativeElement.textContent).toContain('Belkacem');
  });

  it('compose les initiales à partir du prénom et du nom', () => {
    expect(component.getInitials()).toBe('AB');
  });

  it('retombe sur « XX » quand le profil n\'a pas de nom', () => {
    // L'avatar par défaut ne doit jamais rendre une chaîne vide : sans repli, la pastille
    // colorée apparaîtrait sans lettre.
    fixture.componentRef.setInput('profile', aProfile({ firstName: '', lastName: '' }));
    expect(component.getInitials()).toBe('XX');
  });

  it('navigue vers la fiche du profil, préfixée par son type', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');

    component.navigateToProfile();

    expect(navigate).toHaveBeenCalledWith(['/student', '1']);
  });

  it('ne navigue pas quand le profil n\'a pas d\'identifiant', () => {
    const router = TestBed.inject(Router);
    const navigate = spyOn(router, 'navigate');
    fixture.componentRef.setInput('profile', aProfile({ id: undefined }));

    component.navigateToProfile();

    expect(navigate).not.toHaveBeenCalled();
  });

  it('associe une icône à chaque statut de paiement, et rien en son absence', () => {
    expect(component.getPaymentIcon()).toBe('');

    fixture.componentRef.setInput('paymentStatus', aPaymentStatus({ paymentStatus: 'GOOD' }));
    expect(component.getPaymentIcon()).toBe('check_circle');

    fixture.componentRef.setInput('paymentStatus', aPaymentStatus({ paymentStatus: 'LATE' }));
    expect(component.getPaymentIcon()).toBe('warning');

    fixture.componentRef.setInput('paymentStatus', aPaymentStatus({ paymentStatus: 'EXEMPT' }));
    expect(component.getPaymentIcon()).toBe('volunteer_activism');
  });

  it('bascule le retournement de la carte sans propager le clic', () => {
    // Le clic sur la carte navigue vers la fiche : sans stopPropagation, retourner la carte
    // quitterait la page.
    const event = new MouseEvent('click');
    const stopPropagation = spyOn(event, 'stopPropagation');

    expect(component.isFlipped).toBeFalse();
    component.toggleFlip(event);

    expect(component.isFlipped).toBeTrue();
    expect(stopPropagation).toHaveBeenCalled();
  });
});
