import { Injectable, Injector, inject } from '@angular/core';
import { HttpEvent, HttpInterceptor, HttpHandler, HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';

@Injectable()
export class ApiErrorInterceptor implements HttpInterceptor {
  // Lu à la demande : TranslateService charge ses fichiers par HttpClient, donc par cet intercepteur.
  private readonly injector = inject(Injector);
  constructor(private snackBar: MatSnackBar) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    return next.handle(req).pipe(
      catchError((error: HttpErrorResponse) => {
        const translate = this.injector.get(TranslateService);
        this.snackBar.open(translate.instant('HTTP_ERRORS.GENERIC'), translate.instant('common.close'), {
          duration: 3000,
          panelClass: ['error-snackbar'],
        });
        return throwError(error);
      })
    );
  }
}