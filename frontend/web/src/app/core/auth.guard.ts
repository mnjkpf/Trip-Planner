import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { catchError, map, of } from 'rxjs';
import { AuthService } from './auth.service';

/** Не пускає на захищені сторінки без токена — редірект на /login. */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) {
    return true;
  }
  router.navigate(['/login']);
  return false;
};

/**
 * Пускає й без акаунта: якщо токена немає — мовчки заводить гостьову сесію.
 *
 * Висить лише на створенні подорожі, а не на всьому розділі: заводити рядок
 * у users кожному, хто просто зайшов на /trips, було б марнотратно. Тут же
 * людина явно почала щось робити, і подорож має кудись зберегтися.
 * Після цього звичайний authGuard на решті сторінок уже пропускає її.
 */
export const guestOkGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  if (auth.isLoggedIn()) {
    return true;
  }
  return auth.loginAsGuest().pipe(
    map(() => true),
    // Не вдалося (ліміт, мережа) — краще показати логін, ніж порожню сторінку.
    catchError(() => of(router.createUrlTree(['/login']))),
  );
};
