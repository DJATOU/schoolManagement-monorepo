import { ComponentFixture, TestBed } from '@angular/core/testing';

import { SummaryDialogComponent } from './summary-dialog.component';
import { createDialogRefSpy, DialogRefSpy, matDialogProviders, setupComponentTestBed } from '../../../testing/setup';

/**
 * Récapitulatif affiché avant l'enregistrement d'un formulaire.
 *
 * <p>Ses données arrivent sous forme de liste plate d'étiquettes « Section - Champ », que le
 * constructeur regroupe et filtre. Deux filtrages comptent : les champs techniques
 * (`photo`, `file`) et les valeurs vides. Sans eux, l'administrateur relit un récapitulatif
 * encombré de lignes vides et d'un nom de fichier, où l'essentiel se perd.</p>
 */
describe('SummaryDialogComponent', () => {
  let component: SummaryDialogComponent;
  let fixture: ComponentFixture<SummaryDialogComponent>;
  let dialogRef: DialogRefSpy;

  async function build(data: { label: string; value: unknown }[]): Promise<void> {
    // Le regroupement se fait dans le constructeur : chaque jeu de données demande donc une
    // instance neuve, et le `TestBed` doit être réinitialisé avant d'être reconfiguré.
    TestBed.resetTestingModule();
    dialogRef = createDialogRefSpy();
    await setupComponentTestBed(SummaryDialogComponent, {
      providers: matDialogProviders(data, dialogRef)
    });
    fixture = TestBed.createComponent(SummaryDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  beforeEach(async () => {
    await build([
      { label: 'basicInformation - firstName', value: 'Amina' },
      { label: 'basicInformation - lastName', value: 'Belkacem' },
      { label: 'contact - email', value: 'amina@example.test' }
    ]);
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('regroupe les champs par section', () => {
    expect(component.sections.map(section => section.title))
      .toEqual(['basicInformation', 'contact']);
    expect(component.sections[0].fields.map(field => field.label))
      .toEqual(['firstName', 'lastName']);
  });

  it('écarte les champs techniques', async () => {
    await build([
      { label: 'basicInformation - firstName', value: 'Amina' },
      { label: 'basicInformation - photo', value: 'amina.png' },
      { label: 'basicInformation - file', value: 'blob:1234' }
    ]);

    expect(component.sections[0].fields.map(field => field.label)).toEqual(['firstName']);
  });

  it('écarte les valeurs vides, nulles et indéfinies', async () => {
    await build([
      { label: 'contact - email', value: 'amina@example.test' },
      { label: 'contact - phoneNumber', value: '   ' },
      { label: 'contact - address', value: null },
      { label: 'contact - city', value: undefined }
    ]);

    expect(component.sections[0].fields.map(field => field.label)).toEqual(['email']);
  });

  it('n\'affiche pas une section devenue vide après filtrage', async () => {
    await build([
      { label: 'basicInformation - firstName', value: 'Amina' },
      { label: 'media - photo', value: 'amina.png' }
    ]);

    expect(component.sections.map(section => section.title)).toEqual(['basicInformation']);
  });

  it('rend le camelCase lisible quand aucune traduction n\'existe', () => {
    // Repli assumé : mieux vaut « First Name » que la clé brute « SUMMARY.FIELDS.firstName ».
    expect(component.translateField('firstName')).toBe('First Name');
    expect(component.translateSection('basicInformation')).toBe('Basic Information');
  });

  it('ferme sur « vrai » à la confirmation et « faux » à l\'annulation', () => {
    component.onConfirm();
    expect(dialogRef.close).toHaveBeenCalledWith(true);

    component.onCancel();
    expect(dialogRef.close).toHaveBeenCalledWith(false);
  });
});
