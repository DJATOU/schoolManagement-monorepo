import { ComponentFixture, TestBed } from '@angular/core/testing';

import { ProfileListItemComponent } from './profile-list-item.component';
import { setupComponentTestBed } from '../../../../testing/setup';
import { aProfile } from '../../../../testing/fixtures';

/**
 * Variante en liste de la carte de profil. `ngOnInit` déréférence `profile` pour composer
 * les initiales et l'URL de la photo.
 */
describe('ProfileListItemComponent', () => {
  let component: ProfileListItemComponent;
  let fixture: ComponentFixture<ProfileListItemComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(ProfileListItemComponent);
    fixture = TestBed.createComponent(ProfileListItemComponent);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('profile', aProfile());
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('affiche le nom du profil', () => {
    expect(fixture.nativeElement.textContent).toContain('Amina');
  });

  it('compose les initiales à partir du prénom et du nom', () => {
    expect(component.getInitials()).toBe('AB');
  });

  it('ne construit aucune URL de photo quand le profil n\'en a pas', () => {
    // Une URL construite sur une photo absente produirait une image cassée ; le composant
    // doit retomber sur les initiales.
    expect(component.profilePhotoUrl).toBe('');
  });

  it('construit l\'URL de la photo quand elle existe', () => {
    const withPhoto = TestBed.createComponent(ProfileListItemComponent);
    withPhoto.componentRef.setInput('profile', aProfile({ photo: 'amina.png' }));
    withPhoto.detectChanges();

    expect(withPhoto.componentInstance.profilePhotoUrl).toContain('amina.png');
  });

  it('cible « student » par défaut', () => {
    expect(component.profileType).toBe('student');
  });
});
