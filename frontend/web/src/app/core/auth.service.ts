import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthResponse } from './models';

const TOKEN_KEY = 'tp_token';

/**
 * Дістає claim із payload'а JWT. Підпис НЕ перевіряємо — це робить gateway;
 * тут значення потрібне лише для того, щоб намалювати правильну кнопку.
 */
function claim(token: string | null, name: string): unknown {
  if (!token) return undefined;
  try {
    const payload = token.split('.')[1];
    const json = atob(payload.replace(/-/g, '+').replace(/_/g, '/'));
    return JSON.parse(json)[name];
  } catch {
    return undefined;
  }
}

/**
 * Тримає access-токен. Зберігаємо в localStorage, щоб переживати перезавантаження.
 * Стан — сигнал, тож шапка й guard реагують миттєво.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private tokenSig = signal<string | null>(this.read());

  readonly isLoggedIn = computed(() => this.tokenSig() !== null);

  /**
   * Тимчасовий акаунт, створений через «спланувати без реєстрації».
   * Прапорець читаємо з claim'а в токені, а не тримаємо окремо: так він
   * не може розійтися з тим, що насправді думає бекенд.
   */
  readonly isGuest = computed(() => claim(this.tokenSig(), 'guest') === true);

  get token(): string | null {
    return this.tokenSig();
  }

  register(email: string, password: string, displayName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/user/register-user', { email, password, displayName })
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  login(email: string, password: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/login', { email, password })
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  /** Обмінює Google ID-token на наш JWT (бекенд перевіряє підпис Google). */
  loginWithGoogle(idToken: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/google', { idToken })
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  /** Гостьова сесія: акаунт без пошти й пароля, щоб планувати одразу. */
  loginAsGuest(): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/auth/guest', {})
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  /** Перетворює гостьовий акаунт на справжній — подорожі лишаються при ньому. */
  claimGuest(email: string, password: string, displayName: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/user/claim-guest', { email, password, displayName })
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  /** Привласнення гостьового акаунта через Google Sign-In. */
  claimGuestWithGoogle(idToken: string): Observable<AuthResponse> {
    return this.http
      .post<AuthResponse>('/api/user/claim-guest-google', { idToken })
      .pipe(tap((r) => this.store(r.accessToken)));
  }

  logout(): void {
    this.tokenSig.set(null);
    try {
      localStorage.removeItem(TOKEN_KEY);
    } catch {
      /* приватний режим — ігноруємо */
    }
  }

  private store(token: string): void {
    this.tokenSig.set(token);
    try {
      localStorage.setItem(TOKEN_KEY, token);
    } catch {
      /* ignore */
    }
  }

  private read(): string | null {
    try {
      return localStorage.getItem(TOKEN_KEY);
    } catch {
      return null;
    }
  }
}
