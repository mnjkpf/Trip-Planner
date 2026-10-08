import { AfterViewInit, Component, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { DraftService } from '../core/draft.service';
import { GoogleAuthService } from '../core/google-auth.service';
import { LangService } from '../core/lang.service';

@Component({
  selector: 'app-login',
  imports: [FormsModule, RouterLink, TranslatePipe],
  template: `
    <div class="auth-split">
      <div class="auth-poster">
        <div class="brand">WAY<span>/</span>LO</div>
        <h2 class="poster-h">{{ 'app.subtitle' | translate }}</h2>
        <div class="poster-mono">JWT · access 15m / refresh 30d</div>
      </div>
      <div class="auth-form">
        <h1>{{ 'auth.login_title' | translate }}</h1>
        <p class="sub">{{ 'auth.login_subtitle' | translate }}</p>
        <form (ngSubmit)="submit()" #f="ngForm">
          <div class="field">
            <label>{{ 'auth.email' | translate }}</label>
            <input type="email" name="email" [(ngModel)]="email" required autocomplete="email" />
          </div>
          <div class="field">
            <label>{{ 'auth.password' | translate }}</label>
            <input type="password" name="password" [(ngModel)]="password" required autocomplete="current-password" />
          </div>
          @if (error()) { <p class="error">{{ error()! | translate }}</p> }
          <button class="btn btn-primary btn-block" type="submit" [disabled]="loading() || f.invalid">
            {{ loading() ? ('common.sending' | translate) : ('auth.login_btn' | translate) }}
          </button>
        </form>
        @if (google.enabled()) {
          <div class="oauth-split"><span>{{ 'auth.or' | translate }}</span></div>
          <div #googleBtn class="oauth-btn"></div>
          @if (googleErr()) { <p class="error">{{ googleErr()! | translate }}</p> }
        }
        <hr class="hr" />
        <p class="foot">{{ 'auth.need_account' | translate }}
          <a routerLink="/register">{{ 'auth.register_link' | translate }}</a>
        </p>
        <p class="foot"><a routerLink="/places">{{ 'auth.browse_as_guest' | translate }}</a></p>
      </div>
    </div>
  `,
  styles: [`
    .oauth-split { display: flex; align-items: center; gap: 12px; margin: 18px 0 14px; color: var(--muted); font-size: 11px; letter-spacing: 0.08em; text-transform: uppercase; }
    .oauth-split::before, .oauth-split::after { content: ''; flex: 1; height: 1px; background: var(--divider-soft); }
    .oauth-btn { display: flex; justify-content: center; min-height: 44px; }
  `],
})
export class Login implements AfterViewInit {
  private auth = inject(AuthService);
  private router = inject(Router);
  private drafts = inject(DraftService);
  protected google = inject(GoogleAuthService);
  private lang = inject(LangService);
  private googleBtn = viewChild<ElementRef<HTMLDivElement>>('googleBtn');
  googleErr = signal<string | null>(null);

  async ngAfterViewInit(): Promise<void> {
    const host = this.googleBtn()?.nativeElement;
    if (!host || !this.google.enabled()) return;
    try {
      await this.google.renderButton(host, (idToken) => this.onGoogle(idToken), this.lang.current());
    } catch {
      this.googleErr.set('auth.google_load_error');
    }
  }

  private onGoogle(idToken: string): void {
    this.googleErr.set(null);
    this.auth.loginWithGoogle(idToken).subscribe({
      next: () => this.afterAuth(),
      error: () => this.googleErr.set('auth.google_error'),
    });
  }
  email = '';
  password = '';
  error = signal<string | null>(null);
  loading = signal(false);

  submit() {
    this.loading.set(true);
    this.error.set(null);
    this.auth.login(this.email, this.password).subscribe({
      next: () => this.afterAuth(),
      error: () => {
        // Ключ i18n — щоб повідомлення теж було багатомовним
        this.error.set('auth.login_error');
        this.loading.set(false);
      },
    });
  }

  /**
   * Куди вести після входу. Якщо людина прийшла сюди саме щоб зберегти
   * спланований маршрут — повертаємо її до нього, а не в порожній список.
   */
  private afterAuth(): void {
    this.router.navigate([this.drafts.hasDraft() ? '/preview' : '/trips']);
  }
}
