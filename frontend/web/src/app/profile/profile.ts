import { Component, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { LangService } from '../core/lang.service';
import { UserProfile } from '../core/models';
import { UserService } from '../core/user.service';

/**
 * Сторінка профілю. Три незалежні секції: загальна інфа + мова,
 * зміна паролю, видалення акаунту. Кожна з власним станом успіху/помилки,
 * щоб помилка в одній не ламала іншу.
 */
@Component({
  selector: 'app-profile',
  imports: [FormsModule, DatePipe, TranslatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <div class="kicker">{{ 'profile.kicker' | translate }}</div>
          <h1>{{ 'profile.title' | translate }}</h1>
        </div>
      </div>

      @if (loading()) {
        <div class="section"><p class="muted">{{ 'common.loading' | translate }}</p></div>
      } @else if (profile(); as p) {
        <!-- 1. Загальне -->
        <section class="section pf-section">
          <h3>{{ 'profile.general' | translate }}</h3>
          <div class="pf-row">
            <label>{{ 'auth.email' | translate }}</label>
            <input class="input" [value]="p.email" disabled />
            <span class="hint">{{ 'profile.email_locked' | translate }}</span>
          </div>
          <div class="pf-row">
            <label>{{ 'auth.name' | translate }}</label>
            <input class="input" [(ngModel)]="displayName" maxlength="120" />
          </div>
          <div class="pf-row">
            <label>{{ 'nav.language' | translate }}</label>
            <select class="input" [(ngModel)]="prefLang">
              @for (l of lang.langs; track l.code) {
                <option [value]="l.code">{{ l.label }}</option>
              }
            </select>
            <span class="hint">{{ 'profile.lang_hint' | translate }}</span>
          </div>
          <div class="pf-row pf-meta mono">
            {{ 'profile.member_since' | translate }} {{ p.createdAt | date: 'd MMM y' }} · role: {{ p.role }}
          </div>
          <div class="pf-actions">
            <button class="btn btn-primary" (click)="saveProfile()" [disabled]="savingProfile()">
              {{ savingProfile() ? ('common.sending' | translate) : ('common.save' | translate) }}
            </button>
            @if (profileMsg()) { <span class="ok">{{ profileMsg()! | translate }}</span> }
            @if (profileErr()) { <span class="error">{{ profileErr()! | translate }}</span> }
          </div>
        </section>

        <!-- 2. Пароль (ховаємо для Google-акаунтів — локального пароля немає) -->
        @if (!p.oauthProvider) {
        <section class="section pf-section">
          <h3>{{ 'profile.change_password' | translate }}</h3>
          <div class="pf-row">
            <label>{{ 'profile.current_password' | translate }}</label>
            <input class="input" type="password" [(ngModel)]="currentPass" autocomplete="current-password" />
          </div>
          <div class="pf-row">
            <label>{{ 'profile.new_password' | translate }} (min 8)</label>
            <input class="input" type="password" [(ngModel)]="newPass" minlength="8" autocomplete="new-password" />
          </div>
          <div class="pf-actions">
            <button class="btn btn-primary" (click)="savePassword()"
                    [disabled]="savingPass() || !currentPass || (newPass?.length ?? 0) < 8">
              {{ savingPass() ? ('common.sending' | translate) : ('profile.change_password_btn' | translate) }}
            </button>
            @if (passMsg()) { <span class="ok">{{ passMsg()! | translate }}</span> }
            @if (passErr()) { <span class="error">{{ passErr()! | translate }}</span> }
          </div>
          <p class="hint">{{ 'profile.change_password_hint' | translate }}</p>
        </section>
        } @else {
        <section class="section pf-section">
          <h3>{{ 'profile.change_password' | translate }}</h3>
          <p class="hint">{{ 'profile.google_account' | translate }}</p>
        </section>
        }

        <!-- 3. Небезпечна зона -->
        <section class="section pf-section pf-danger">
          <h3>{{ 'profile.danger_zone' | translate }}</h3>
          <p class="hint">{{ 'profile.delete_hint' | translate }}</p>
          @if (!confirmingDelete()) {
            <button class="btn btn-danger" (click)="confirmingDelete.set(true)">
              {{ 'profile.delete_btn' | translate }}
            </button>
          } @else {
            <div class="pf-confirm">
              <span>{{ 'profile.delete_confirm' | translate }}</span>
              <button class="btn btn-danger btn-sm" (click)="deleteAccount()" [disabled]="deleting()">
                {{ deleting() ? '…' : ('common.yes' | translate) }}
              </button>
              <button class="btn btn-secondary btn-sm" (click)="confirmingDelete.set(false)" [disabled]="deleting()">
                {{ 'common.no' | translate }}
              </button>
            </div>
          }
          @if (deleteErr()) { <span class="error">{{ deleteErr()! | translate }}</span> }
        </section>
      } @else if (error()) {
        <div class="section"><p class="error">{{ error()! | translate }}</p></div>
      }
    </div>
  `,
  styles: [`
    .pf-section { max-width: 640px; }
    .pf-section + .pf-section { border-top: 2px solid var(--divider); }
    .pf-section h3 { margin: 0 0 18px; font-size: 20px; }
    .pf-row { display: flex; flex-direction: column; gap: 4px; margin-bottom: 14px; }
    .pf-row label { font-size: 12px; color: var(--muted); }
    .pf-row .hint { font-size: 11px; color: var(--muted); margin-top: 4px; }
    .input { min-height: 36px; padding: 6px 10px; font: inherit; font-size: 14px;
      border: 1px solid var(--divider); background: var(--surface); }
    .input:focus { outline: none; border-color: var(--accent); }
    .input:disabled { opacity: 0.6; cursor: not-allowed; }
    .pf-meta { font-size: 11px; color: var(--muted); margin: 10px 0; }
    .pf-actions { display: flex; gap: 12px; align-items: center; margin-top: 8px; }
    .ok { color: var(--accent-700); font-weight: 600; font-size: 13px; }
    .pf-danger { background: color-mix(in srgb, var(--accent-700) 4%, transparent); }
    .pf-danger h3 { color: var(--accent-700); }
    .pf-confirm { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
  `],
})
export class Profile {
  private users = inject(UserService);
  private auth = inject(AuthService);
  private router = inject(Router);
  protected lang = inject(LangService);

  profile = signal<UserProfile | null>(null);
  loading = signal(true);
  error = signal<string | null>(null);

  displayName = '';
  prefLang = '';
  savingProfile = signal(false);
  profileMsg = signal<string | null>(null);
  profileErr = signal<string | null>(null);

  currentPass = '';
  newPass = '';
  savingPass = signal(false);
  passMsg = signal<string | null>(null);
  passErr = signal<string | null>(null);

  confirmingDelete = signal(false);
  deleting = signal(false);
  deleteErr = signal<string | null>(null);

  constructor() {
    this.users.me().subscribe({
      next: (p) => {
        this.profile.set(p);
        this.displayName = p.displayName ?? '';
        this.prefLang = p.preferredLanguage ?? this.lang.current();
        this.loading.set(false);
      },
      error: () => {
        this.error.set('profile.load_error');
        this.loading.set(false);
      },
    });
  }

  saveProfile(): void {
    this.savingProfile.set(true);
    this.profileMsg.set(null);
    this.profileErr.set(null);
    this.users.update({ displayName: this.displayName, preferredLanguage: this.prefLang }).subscribe({
      next: (p) => {
        this.profile.set(p);
        this.savingProfile.set(false);
        this.profileMsg.set('profile.saved');
        // Якщо мова змінилась — перемикаємо негайно
        if (this.prefLang && this.prefLang !== this.lang.current()) {
          this.lang.use(this.prefLang);
        }
      },
      error: () => {
        this.savingProfile.set(false);
        this.profileErr.set('profile.save_error');
      },
    });
  }

  savePassword(): void {
    this.savingPass.set(true);
    this.passMsg.set(null);
    this.passErr.set(null);
    this.users.changePassword({ currentPassword: this.currentPass, newPassword: this.newPass }).subscribe({
      next: () => {
        this.savingPass.set(false);
        this.passMsg.set('profile.password_saved');
        this.currentPass = ''; this.newPass = '';
      },
      error: (e) => {
        this.savingPass.set(false);
        this.passErr.set(e?.status === 400 ? 'profile.password_wrong' : 'profile.save_error');
      },
    });
  }

  deleteAccount(): void {
    this.deleting.set(true);
    this.deleteErr.set(null);
    this.users.deleteAccount().subscribe({
      next: () => {
        this.auth.logout();
        this.router.navigate(['/login']);
      },
      error: () => {
        this.deleting.set(false);
        this.deleteErr.set('profile.delete_error');
      },
    });
  }
}
