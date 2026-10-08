import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from '../core/auth.service';
import { DraftService } from '../core/draft.service';
import { TripService } from '../core/trip.service';

/**
 * Маршрут, який гість спланував без акаунта.
 *
 * Дані беруться з localStorage, а не з бекенда: до реєстрації подорожі не
 * існує ніде, крім цієї вкладки. Сторінка про це прямо каже — інакше людина
 * вважала б, що все вже збережено, і втратила б роботу при чистці браузера.
 *
 * Якщо людина вже залогінена (наприклад, щойно зареєструвалась і повернулась
 * сюди), замість попередження показуємо кнопку збереження: саме тут чернетка
 * перетворюється на справжню подорож через звичайний create + plan.
 */
@Component({
  selector: 'app-trip-preview',
  imports: [RouterLink, TranslatePipe],
  template: `
    @if (draftSvc.draft(); as d) {
      <div class="page">
        <div class="pv-head">
          <div>
            <div class="kicker">{{ 'preview.kicker' | translate }}</div>
            <h1>{{ d.request.title }}</h1>
            <p class="sub mono">
              {{ d.request.destinationName }} · {{ d.request.startDate }} → {{ d.request.endDate }}
              @if (d.preview.season) { · {{ d.preview.season }} }
            </p>
          </div>
        </div>

        <div class="pv-banner" [class.saved-path]="auth.isLoggedIn()">
          @if (auth.isLoggedIn()) {
            <span>{{ 'preview.ready_to_save' | translate }}</span>
            <button class="btn btn-primary btn-sm" (click)="save()" [disabled]="saving()">
              {{ saving() ? ('preview.saving' | translate) : ('preview.save' | translate) }}
            </button>
          } @else {
            <span>{{ 'preview.not_saved' | translate }}</span>
            <a class="btn btn-primary btn-sm" routerLink="/register">{{ 'preview.create_account' | translate }}</a>
          }
        </div>
        @if (error()) { <p class="error">{{ error()! | translate }}</p> }

        @if (d.preview.climateHint) {
          <p class="pv-hint">{{ d.preview.climateHint }}</p>
        }

        @for (day of d.preview.days; track day.dayNumber) {
          <section class="pv-day">
            <h2>{{ 'preview.day' | translate }} {{ day.dayNumber }} <span class="mono">{{ day.date }}</span></h2>
            @if (day.items.length === 0) {
              <p class="muted">{{ 'preview.empty_day' | translate }}</p>
            }
            @for (it of day.items; track it.placeId) {
              <div class="pv-item">
                @if (it.imageUrl) {
                  <img class="pv-photo" [src]="it.imageUrl" [alt]="it.name" (error)="hideImage($event)" />
                }
                <div class="pv-body">
                  <div class="pv-name">{{ it.name }}</div>
                  <div class="pv-meta mono">
                    @if (it.plannedStart) { {{ it.plannedStart }}–{{ it.plannedEnd }} · }
                    {{ it.dwellMinutes }}{{ 'preview.min' | translate }}
                    @if (it.travelMinutesFromPrev > 0) { · +{{ it.travelMinutesFromPrev }}{{ 'preview.walk' | translate }} }
                  </div>
                </div>
              </div>
            }
          </section>
        }

        <div class="pv-foot">
          <a class="btn btn-ghost btn-sm" routerLink="/trips/new">{{ 'preview.replan' | translate }}</a>
          <button class="btn btn-ghost btn-sm" (click)="discard()">{{ 'preview.discard' | translate }}</button>
        </div>
      </div>
    } @else {
      <div class="page">
        <h1>{{ 'preview.none_title' | translate }}</h1>
        <p class="sub">{{ 'preview.none_body' | translate }}</p>
        <a class="btn btn-primary" routerLink="/trips/new">{{ 'nav.plan_trip' | translate }}</a>
      </div>
    }
  `,
  styles: [`
    .pv-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; margin-bottom: 14px; }
    .pv-banner {
      display: flex; align-items: center; justify-content: space-between; gap: 14px; flex-wrap: wrap;
      border: 2px solid var(--ink); padding: 12px 14px; margin-bottom: 18px;
      font-size: 13px; background: var(--accent-100);
    }
    .pv-banner.saved-path { background: var(--surface); }
    .pv-hint { font-size: 12px; color: var(--muted); margin: 0 0 16px; }
    .pv-day { margin-bottom: 22px; }
    .pv-day h2 { font-size: 15px; margin: 0 0 10px; display: flex; gap: 10px; align-items: baseline; }
    .pv-day h2 .mono { font-size: 11px; color: var(--muted); font-weight: 400; }
    .pv-item { display: flex; gap: 12px; align-items: center; border-bottom: 1px solid var(--divider-soft); padding: 9px 0; }
    .pv-photo { width: 56px; height: 42px; object-fit: cover; flex: none; }
    .pv-name { font: 700 13px/1.3 var(--font); }
    .pv-meta { font-size: 11px; color: var(--muted); }
    .pv-foot { display: flex; gap: 10px; margin-top: 24px; }
    .muted { color: var(--muted); font-size: 12px; }
  `],
})
export class TripPreview {
  protected auth = inject(AuthService);
  protected draftSvc = inject(DraftService);
  private trips = inject(TripService);
  private router = inject(Router);

  saving = signal(false);
  error = signal<string | null>(null);

  /**
   * Перетворює чернетку на справжню подорож. Маршрут не переносимо, а
   * переплановуємо звичайним шляхом: збережена подорож має пройти ту саму
   * асинхронну гілку, що й будь-яка інша, інакше в неї не буде ні job'а,
   * ні подій, на які спирається решта системи. Прев'ю було прев'ю.
   */
  save(): void {
    const d = this.draftSvc.draft();
    if (!d || this.saving()) return;
    this.saving.set(true);
    this.error.set(null);
    this.trips.create(d.request).subscribe({
      next: (t) => {
        this.draftSvc.clear();
        this.trips.plan(t.id).subscribe({
          next: () => this.router.navigate(['/trips', t.id]),
          error: () => this.router.navigate(['/trips', t.id]),
        });
      },
      error: () => {
        this.error.set('preview.save_error');
        this.saving.set(false);
      },
    });
  }

  discard(): void {
    this.draftSvc.clear();
    this.router.navigate(['/places']);
  }

  hideImage(ev: Event): void {
    (ev.target as HTMLImageElement).style.display = 'none';
  }
}
