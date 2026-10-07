import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';
import { RequestLogService } from './request-log.service';

/**
 * Додає Bearer до всіх /api-запитів, коли токен є (gateway його валідує),
 * пише виклик у журнал подій і на 401 робить чистий вихід + редірект на логін.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  const log = inject(RequestLogService);

  if (req.url.startsWith('/api')) {
    log.push(req.method, req.url);
  }

  const token = auth.token;
  if (token && req.url.startsWith('/api')) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }

  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      const isAuthCall = req.url.includes('/api/auth/') || req.url.includes('/register-user');
      if (err.status === 401 && !isAuthCall) {
        auth.logout();
        router.navigate(['/login']);
      }
      return throwError(() => err);
    }),
  );
};
