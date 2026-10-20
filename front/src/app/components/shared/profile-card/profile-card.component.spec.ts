import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';

import { ProfileCardComponent } from './profile-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aPaymentStatus, aProfile } from '../../../../testing/fixtures';
import { By } from '@angular/platform-browser';
import { MatTooltip } from '@angular/material/tooltip';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';

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

  /** Infobulles et sous-titre traduits : en français sous l'interface française, l'anglais suit la langue. */
  it('traduit infobulles et sous-titre, en français puis en anglais', () => {
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
    fixture.componentRef.setInput('profile', aProfile({ subtitleKey: 'PROFILE_CARD.LEVEL', subtitleParams: { level: '2AS' } }));
    fixture.detectChanges();
    const tooltips = () => fixture.debugElement.queryAll(By.directive(MatTooltip))
      .map(element => element.injector.get(MatTooltip).message);
    const subtitle = () => (fixture.nativeElement.querySelector('.card-subtitle') as HTMLElement).textContent?.trim();

    expect(tooltips()).toContain('Envoyer un email');
    expect(tooltips()).toContain('Ouvrir WhatsApp');
    expect(subtitle()).toBe('Niveau : 2AS');

    translate.use('en');
    fixture.detectChanges();
    expect(tooltips()).toContain('Send an email');
    expect(tooltips()).toContain('Open WhatsApp');
    expect(subtitle()).toBe('Level: 2AS');
  });

  it('sans clé de sous-titre, affiche le sous-titre tel quel', () => {
    expect((fixture.nativeElement.querySelector('.card-subtitle') as HTMLElement).textContent?.trim()).toBe('Niveau : 2AS');
  });
});
