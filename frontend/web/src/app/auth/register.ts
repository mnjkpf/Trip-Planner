import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { AuthService } from '../core/auth.service';

@Component({
  selector: 'app-register',
  imports: [FormsModule, RouterLink],
  template: `
    <div class="card auth-card">
      <h1>Реєстрація</h1>
      <form (ngSubmit)="submit()" #f="ngForm">
        <label>Ім'я
          <input name="displayName" [(ngModel)]="displayName" required />
        </label>
        <label>Пошта
          <input type="email" name="email" [(ngModel)]="email" required autocomplete="email" />
        </label>
        <label>Пароль (мін. 8)
          <input type="password" name="password" [(ngModel)]="password" required minlength="8" autocomplete="new-password" />
        </label>
        @if (error()) { <p class="error">{{ error() }}</p> }
        <button class="primary" type="submit" [disabled]="loading() || f.invalid">
          {{ loading() ? 'Створюємо…' : 'Зареєструватися' }}
        </button>
      </form>
      <p class="muted">Вже є акаунт? <a routerLink="/login">Увійти</a></p>
    </div>
  `,
})
export class Register {
  private auth = inject(AuthService);
  private router = inject(Router);
  displayName = '';
  email = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  submit() {
    this.loading.set(true);
    this.error.set(null);
    this.auth.register(this.email, this.password, this.displayName).subscribe({
      next: () => this.router.navigate(['/trips']),
      error: () => {
        this.error.set('Не вдалося зареєструватися (можливо, пошта вже зайнята)');
        this.loading.set(false);
      },
    });
  }
}
