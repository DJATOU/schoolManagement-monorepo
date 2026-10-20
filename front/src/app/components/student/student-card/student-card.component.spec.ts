import { ComponentFixture, TestBed } from '@angular/core/testing';

import { StudentCardComponent } from './student-card.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aStudent } from '../../../../testing/fixtures';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';

/**
 * `student` est une entrée obligatoire, déréférencée par `ngOnInit`.
 *
 * <p>La fixture laisse `levelId` et `tutorId` indéfinis : renseignés, ils déclenchent des
 * appels HTTP dont la réponse conditionne la construction du profil. Le chemin nominal se
 * teste donc sans réseau, et les tests qui ont besoin du chargement les fournissent.</p>
 */
describe('StudentCardComponent', () => {
  let component: StudentCardComponent;
  let fixture: ComponentFixture<StudentCardComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(StudentCardComponent);
    fixture = TestBed.createComponent(StudentCardComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('student', aStudent());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('projette l\'étudiant sur le profil de la carte partagée', () => {
    expect(component.profile).toEqual(jasmine.objectContaining({
      id: 1,
      firstName: 'Amina',
      lastName: 'Belkacem',
      email: 'amina.belkacem@example.test'
    }));
  });

  it('porte le niveau en sous-titre, en français puis en anglais', () => {
    expect(component.profile.subtitleParams).toEqual({ level: '2AS' });
    const translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
    fixture.detectChanges();
    const subtitle = () => (fixture.nativeElement.querySelector('.card-subtitle') as HTMLElement).textContent?.trim();
    expect(subtitle()).toBe('Niveau : 2AS');
    translate.use('en');
    fixture.detectChanges();
    expect(subtitle()).toBe('Level: 2AS');
  });

  it('sans niveau, le sous-titre l\'indique', () => {
    const card = TestBed.createComponent(StudentCardComponent);
    card.componentRef.setInput('student', aStudent({ levelName: undefined }));
    card.detectChanges();
    expect(card.componentInstance.profile.subtitleKey).toBe('PROFILE_CARD.LEVEL_UNKNOWN');
  });

  it('n\'expose aucun tuteur quand l\'étudiant n\'en a pas', () => {
    // `undefined` signifierait « pas encore chargé » ; `null` dit « aucun tuteur », ce que
    // le dos de la carte doit pouvoir distinguer.
    expect(component.tutor).toBeNull();
  });

  it('affiche le nom de l\'étudiant', () => {
    expect(fixture.nativeElement.textContent).toContain('Amina');
    expect(fixture.nativeElement.textContent).toContain('Belkacem');
  });
});
