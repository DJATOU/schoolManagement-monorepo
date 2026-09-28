import { ComponentFixture, TestBed } from '@angular/core/testing';

import { NavigationComponent } from './navigation.component';
import { setupComponentTestBed } from '../../../testing/setup';

describe('NavigationComponent', () => {
  let component: NavigationComponent;
  let fixture: ComponentFixture<NavigationComponent>;

  beforeEach(async () => {
    await setupComponentTestBed(NavigationComponent);

    fixture = TestBed.createComponent(NavigationComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('démarre sur la recherche d\'étudiant', () => {
    // ngOnInit appelle setSearchType('student') : le libellé et l'icône doivent suivre.
    expect(component.currentSearchType).toBe('student');
    expect(component.placeholderKey).toBe('NAV.SEARCH.PLACEHOLDER_STUDENT');
    expect(component.typeIcon).toBe('school');
  });

  it('setSearchType() réaligne libellé, icône et vide la saisie', () => {
    component.searchControl.setValue('dupont');

    component.setSearchType('teacher');

    expect(component.placeholderKey).toBe('NAV.SEARCH.PLACEHOLDER_TEACHER');
    expect(component.typeIcon).toBe('person');
    expect(component.searchControl.value).toBe('');
  });

  it('initialsOf() prend au plus deux initiales', () => {
    expect(component.initialsOf('Amina Belkacem')).toBe('AB');
    expect(component.initialsOf('Amina')).toBe('AM');
    expect(component.initialsOf('   ')).toBe('?');
  });
});
