import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { AuthResponse } from './models';

const TOKEN_KEY = 'tp_token';

/**
 * Тримає access-токен. Зберігаємо в localStorage, щоб переживати перезавантаження.
 * Стан — сигнал, тож шапка й guard реагують миттєво.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private http = inject(HttpClient);
  private tokenSig = signal<string | null>(this.read());

  readonly isLoggedIn = computed(() => this.tokenSig() !== null);

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
