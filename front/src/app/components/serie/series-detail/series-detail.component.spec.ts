import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog, MatDialogConfig, MatDialogRef } from '@angular/material/dialog';
import { of } from 'rxjs';

import { SeriesDetailComponent } from './series-detail.component';
import { activatedRouteProviders, setupComponentTestBed } from '../../../../testing/setup';
import { aSession } from '../../../../testing/fixtures';
import { Session } from '../../../models/session/session';
import { SessionSeries } from '../../../models/sessionSerie/sessionSerie';
import { SeriesService } from '../../../services/series.service';
import { SessionService } from '../../../services/SessionService';

/**
 * Liste des séances d'une série, rechargée quand la fiche d'une séance change son état (D.3).
 *
 * <p>Une dévalidation laisse la fiche ouverte, la feuille à refaire, et la fiche peut ensuite se
 * fermer sans résultat : la séance restait affichée « validée » jusqu'au rechargement de la page.</p>
 */
describe('SeriesDetailComponent — retour de la fiche d\'une séance', () => {
  let fixture: ComponentFixture<SeriesDetailComponent>;
  let sessionsBySeries: jasmine.Spy;

  beforeEach(async () => {
    await setupComponentTestBed(SeriesDetailComponent, {
      providers: activatedRouteProviders({ groupId: '5', seriesId: '10' })
    });
    sessionsBySeries = spyOn(TestBed.inject(SessionService), 'getSessionsBySeriesId').and.returnValue(of([]));
    spyOn(TestBed.inject(SeriesService), 'getSeriesById').and.returnValue(of({ id: 10 } as SessionSeries));
    fixture = TestBed.createComponent(SeriesDetailComponent);
    fixture.detectChanges();
    sessionsBySeries.calls.reset();
  });

  /** Ouvre la séance ; la fiche fait `change` sur ses données puis se ferme sur `result`. */
  function openThen(session: Session, change: (data: { isFinished?: boolean }) => void, result: unknown): void {
    spyOn(fixture.debugElement.injector.get(MatDialog), 'open').and.callFake(
      ((_component: unknown, config?: MatDialogConfig) => {
        change(config!.data as { isFinished?: boolean });
        return { afterClosed: () => of(result) } as MatDialogRef<unknown>;
      }) as never);
    fixture.componentInstance.openSession(session);
  }

  it('dévalidée dans la fiche, fermée sans résultat : la liste est rechargée', () => {
    openThen(aSession({ isFinished: true }), data => data.isFinished = false, undefined);

    expect(sessionsBySeries).toHaveBeenCalledOnceWith(10);
  });

  it('validée : la liste est rechargée', () => {
    openThen(aSession({ isFinished: false }), data => data.isFinished = true, { isFinished: true });

    expect(sessionsBySeries).toHaveBeenCalledOnceWith(10);
  });

  it('fermée sans changement : rien n\'est rechargé', () => {
    openThen(aSession({ isFinished: true }), () => undefined, undefined);

    expect(sessionsBySeries).not.toHaveBeenCalled();
  });
});
