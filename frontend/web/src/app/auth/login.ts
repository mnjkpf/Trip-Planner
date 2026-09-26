import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="card auth-card">
      <h1>Вхід</h1>
      <form (ngSubmit)="submit()" #f="ngForm">
        <label>Пошта
          <input type="email" name="email" [(ngModel)]="email" required autocomplete="email" />
        </label>
        <label>Пароль
          <input type="password" name="password" [(ngModel)]="password" required autocomplete="current-password" />
        </label>
        @if (error()) { <p class="error">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="loading() || f.invalid">
          {{ loading() ? 'Входимо…' : 'Увійти' }}
        </button>
      </form>
      <p class="muted">Немає акаунта? <a routerLink="/register">Зареєструватися</a></p>
    </div>
  `,
})
export class Login {
  private auth = inject(AuthService);
  private router = inject(Router);
  email = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  submit() {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.router.navigate(['/trips']),
      error: () => {
        this.error.set('Невірна пошта або пароль');
        this.loading.set(false);
      },
    });
  }
}
