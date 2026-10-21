import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpTestingController } from '@angular/common/http/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';

import { TeacherProfileComponent } from './teacher-profile.component';
import { Teacher } from '../../../models/teacher/teacher';
import { API_BASE_URL } from '../../../api-base-url';
import { setupComponentTestBed } from '../../../../testing/setup';
import { ProfilePdfService } from '../../../services/profile-pdf.service';
import { TranslateService } from '@ngx-translate/core';
import frTranslations from '../../../../assets/i18n/fr.json';
import enTranslations from '../../../../assets/i18n/en.json';
import arTranslations from '../../../../assets/i18n/ar.json';
import { pdfLanguage } from '../../../shared/pdf-i18n';
import { choosePrintLanguage } from '../../../../testing/print-language';

describe('TeacherProfileComponent', () => {
  let component: TeacherProfileComponent;
  let fixture: ComponentFixture<TeacherProfileComponent>;
  let httpMock: HttpTestingController;
  let translate: TranslateService;

  const teacher: Teacher = {
    id: 7,
    firstName: 'Karim',
    lastName: 'Belhadj',
    gender: 'M',
    email: 'karim@example.com',
    phoneNumber: '0555000000',
    dateOfBirth: '1980-05-02',
    placeOfBirth: 'Alger',
    specialization: 'Mathématiques',
    yearsOfExperience: 12,
    groups: []
  };

  beforeEach(async () => {
    // La fiche se lit à partir de l'identifiant présent dans l'URL.
    await setupComponentTestBed(TeacherProfileComponent, {
      providers: [{
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ id: '7' }) }, params: { subscribe: () => undefined } }
      }]
    });

    translate = TestBed.inject(TranslateService);
    translate.setTranslation('fr', frTranslations);
    translate.setTranslation('en', enTranslations);
    translate.use('fr');
    fixture = TestBed.createComponent(TeacherProfileComponent);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    fixture.detectChanges();
  });

  /** Sert la fiche enseignant et ses groupes. */
  function flushTeacher(body: Teacher = teacher): void {
    httpMock.expectOne(`${API_BASE_URL}/api/teachers/id/7`).flush(body);
    httpMock.expectOne(`${API_BASE_URL}/api/groups/teacher/7`).flush([]);
    fixture.detectChanges();
  }

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre en chargement puis affiche l\'enseignant de l\'URL', () => {
    expect(component.loading).toBeTrue();

    flushTeacher();

    expect(component.loading).toBeFalse();
    expect(component.teacher).toEqual(teacher);
    expect(fixture.nativeElement.textContent).toContain('Belhadj');
  });

  it('getInitials() combine les deux initiales', () => {
    flushTeacher();

    expect(component.getInitials()).toBe('KB');
  });

  it('sans photo, aucune URL d\'image n\'est construite', () => {
    // Le repli sur les initiales dépend d'une URL vide, pas d'une image cassée.
    flushTeacher();

    expect(component.teacherPhotoUrl).toBe('');
    expect(component.hasImageError).toBeFalse();
  });

  /** Date de naissance : 1985-02-20 s'affiche 20/02/1985, à l'écran comme dans le PDF, quel que soit le fuseau. */
  it('date de naissance : 20/02/1985 sur la fiche et dans le PDF', () => {
    flushTeacher({ ...teacher, dateOfBirth: '1985-02-20' });
    expect(fixture.nativeElement.textContent).toContain('20/02/1985');
    const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf');
    component.printProfilePdf();
    const rows = pdf.calls.mostRecent().args[0].sections.flatMap(section => section.rows);
    expect(rows.find(row => row.label === 'Date de naissance')?.value).toBe('20/02/1985');
  });

  /**
   * Langue de la fiche : le français reste celui d'avant l'externalisation des libellés, l'anglais
   * suit la langue choisie. Le PDF est en français pour toute langue autre que l'anglais.
   */
  describe('langue de la fiche', () => {
    const text = (): string => (fixture.nativeElement.textContent as string).replace(/\s+/g, ' ');

    it('français : libellés identiques à ceux d\'avant', () => {
      flushTeacher();
      expect(text()).toContain('Informations personnelles');
      expect(text()).toContain('Informations professionnelles');
      expect(text()).toContain("Années d'expérience");
      expect(text()).toContain('Groupes enseignés (0)');
      expect(text()).toContain('Aucun groupe enseigné pour le moment');
      expect(text()).toContain('Masculin');
    });

    it('anglais : libellés anglais', () => {
      flushTeacher();
      translate.use('en');
      fixture.detectChanges();
      expect(text()).toContain('Personal information');
      expect(text()).toContain('Years of experience');
      expect(text()).toContain('Groups taught (0)');
      expect(text()).toContain('No groups taught yet');
      expect(text()).toContain('Male');
      expect(text()).not.toContain('Informations personnelles');
    });

    function pdfLabels(): string[] {
      const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf');
      component.printProfilePdf();
      const doc = pdf.calls.mostRecent().args[0];
      return [doc.subtitle ?? '', doc.listTitle ?? '', ...doc.sections.map(section => section.heading),
        ...doc.sections.flatMap(section => section.rows.map(row => `${row.label}=${row.value}`))];
    }

    it('PDF en français sous le français', () => {
      flushTeacher();
      const labels = pdfLabels();
      expect(labels).toContain('Enseignant · Mathématiques');
      expect(labels).toContain('Informations personnelles');
      expect(labels).toContain('Sexe=Masculin');
      expect(labels).toContain("Années d'expérience=12");
      expect(labels).toContain('Groupes enseignés (0)');
    });

    it('PDF en français sous une langue autre que fr / en', () => {
      flushTeacher();
      translate.setTranslation('ar', {});
      translate.use('ar');
      const labels = pdfLabels();
      expect(labels).toContain('Informations personnelles');
      expect(labels).toContain('Sexe=Masculin');
      expect(labels).toContain('Groupes enseignés (0)');
    });

    it('PDF en anglais sous l\'anglais', () => {
      flushTeacher();
      translate.use('en');
      const labels = pdfLabels();
      expect(labels).toContain('Teacher · Mathématiques');
      expect(labels).toContain('Personal information');
      expect(labels).toContain('Gender=Male');
      expect(labels).toContain('Groups taught (0)');
    });

    it('impression : le bouton suit l\'écran (anglais), le menu imprime en arabe sur demande', () => {
      flushTeacher();
      translate.setTranslation('ar', arTranslations);
      translate.use('en');
      fixture.detectChanges();
      const seen: string[] = [];
      const pdf = spyOn(TestBed.inject(ProfilePdfService), 'generateProfilePdf')
        .and.callFake(() => { seen.push(pdfLanguage(translate)); });

      (fixture.nativeElement.querySelector('button.pf-hb-print:not(.plm-trigger)') as HTMLButtonElement).click();
      expect(seen).toEqual(['en']);
      expect(pdf.calls.mostRecent().args[0].sections[0].heading).toBe(enTranslations.TEACHER_PROFILE.PERSONAL_INFO);

      choosePrintLanguage(fixture, '.pf-header-buttons', 'ar');
      expect(seen).toEqual(['en', 'ar']);
      expect(pdf.calls.mostRecent().args[0].sections[0].heading).toBe(arTranslations.TEACHER_PROFILE.PERSONAL_INFO);
      expect(translate.currentLang).toBe('en');
    });
  });
});
