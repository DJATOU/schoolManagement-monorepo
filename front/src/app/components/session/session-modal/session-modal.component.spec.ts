import { ComponentFixture, TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { SessionModalComponent } from './session-modal.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aSession, aStudent } from '../../../../testing/fixtures';

/**
 * Fiche détaillée d'une séance, ouverte depuis le calendrier.
 *
 * <p>Le point sous test est `writeDisabled$` : les commandes d'écriture — édition, validation,
 * saisie de présence — doivent être désactivées si la vue est en lecture seule (année passée)
 * <strong>ou</strong> si l'utilisateur n'est pas administrateur. Un utilisateur non connecté
 * dans un test ne doit donc surtout pas se retrouver avec les droits d'écriture.</p>
 */
describe('SessionModalComponent', () => {
  let component: SessionModalComponent;
  let fixture: ComponentFixture<SessionModalComponent>;
  let dialogRef: DialogRefSpy;

  beforeEach(async () => {
    dialogRef = createDialogRefSpy();
    // Reconstruit à chaque test : un test modifie la séance pour exercer le cas « sans
    // groupe », et un objet partagé propagerait cette mutation aux tests suivants selon leur
    // ordre d'exécution.
    const sessionData = { ...aSession(), students: [aStudent()] };
    await setupComponentTestBed(SessionModalComponent, {
      providers: matDialogProviders(sessionData, dialogRef)
    });
    fixture = TestBed.createComponent(SessionModalComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('porte la séance sur laquelle il a été ouvert', () => {
    expect(component.sessionData.id).toBe(100);
    expect(component.sessionData.groupId).toBe(5);
  });

  it('interdit l\'écriture à un utilisateur sans rôle administrateur', async () => {
    // Le défaut à éviter est l'inverse : autoriser l'écriture par défaut ouvrirait la
    // validation des présences à un simple lecteur.
    await expectAsync(firstValueFrom(component.writeDisabled$)).toBeResolvedTo(true);
  });

  it('n\'ouvre pas la fiche du groupe quand la séance n\'y est pas rattachée', () => {
    const open = spyOn(window, 'open');
    component.sessionData.groupId = undefined as unknown as number;

    component.openGroup();

    expect(open).not.toHaveBeenCalled();
  });
});
