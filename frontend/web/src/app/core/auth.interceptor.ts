import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Додає Bearer до всіх /api-запитів, коли токен є (gateway його валідує).
 * На 401 (протух/невалідний токен) — чистий вихід і редірект на логін,
 * щоб застосунок не показував тиху порожнечу.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

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
