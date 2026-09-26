import { Component, inject, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { switchMap, take, takeWhile, timer } from 'rxjs';
import { TripService } from '../core/trip.service';
import { AuthService } from '../core/auth.service';
import { Itinerary, TravelContext, Trip } from '../core/models';

@Component({
  selector: 'app-trip-detail',
  imports: [DatePipe, RouterLink],
  template: `
    @if (trip(); as t) {
      <a class="back" routerLink="/trips">← До подорожей</a>
      <div class="page-head">
        <div>
          <h1>{{ t.title }}</h1>
          <p class="muted">
            {{ t.destinationName }} · {{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}
          </p>
        </div>
        <span class="badge" [class]="'st-' + t.status">{{ statusLabel(t.status) }}</span>
      </div>

      @if (t.status !== 'PLANNED') {
        <div class="card plan-cta">
          <p>Маршрут ще не побудований.</p>
          <button class="primary" (click)="plan()" [disabled]="planning()">
            {{ planning() ? 'Плануємо…' : 'Спланувати маршрут' }}
          </button>
          @if (planning()) {
            <div class="spinner-row">
              <span class="spinner"></span>
              Обробляється асинхронно: 202 Accepted → outbox → Kafka → planner → назад…
            </div>
          }
        </div>
      }

      @if (error()) { <p class="error">{{ error() }}</p> }

      @if (ctx(); as c) {
        <div class="card weather">
          <h3>Контекст поїздки</h3>
          <p><strong>Сезон:</strong> {{ seasonLabel(c.season) }}</p>
          @if (c.climateHint) { <p class="muted">{{ c.climateHint }}</p> }
          <div class="wx-days">
            @for (d of c.days; track d.date) {
              <div class="wx-day">
                <span class="wx-date">{{ d.date | date: 'd MMM' }}</span>
                @if (d.tempMaxC !== null) {
                  <span class="wx-temp">{{ d.tempMinC }}° / {{ d.tempMaxC }}°</span>
                } @else {
                  <span class="wx-temp muted">лише сезон</span>
                }
              </div>
            }
          </div>
        </div>
      }

      @if (itinerary(); as it) {
        <h2>Маршрут по днях</h2>
        <div class="timeline">
          @for (day of it.days; track day.dayIndex) {
            <div class="day">
              <div class="day-head">День {{ day.dayIndex }} · {{ day.date | date: 'EEEE, d MMM' }}</div>
              @if (day.items.length === 0) {
                <div class="muted no-items">Точок поки нема (база місць порожня).</div>
              } @else {
                <ol class="items">
                  @for (item of day.items; track item.placeId) {
                    <li>
                      <span class="ord">{{ item.order + 1 }}</span>
                      <span class="pname">{{ item.placeName }}</span>
                      @if (item.placeCategory) { <span class="cat">{{ item.placeCategory }}</span> }
                    </li>
                  }
                </ol>
              }
            </div>
          }
        </div>
      }
    } @else {
      <p class="muted">Завантаження…</p>
    }
  `,
})
export class TripDetail {
  private route = inject(ActivatedRoute);
  private trips = inject(TripService);
  private auth = inject(AuthService);

  id = this.route.snapshot.paramMap.get('id')!;
  trip = signal<Trip | null>(null);
  itinerary = signal<Itinerary | null>(null);
  ctx = signal<TravelContext | null>(null);
  planning = signal(false);
  error = signal<string | null>(null);

  constructor() {
    this.load();
  }

  private load() {
    this.trips.get(this.id).subscribe({
      next: (t) => {
        this.trip.set(t);
        if (t.status === 'PLANNED') {
          this.loadItinerary();
          this.loadContext(t);
        }
      },
      error: () => this.error.set('Не вдалося завантажити подорож'),
    });
  }

  plan() {
    this.planning.set(true);
    this.error.set(null);
    this.trips.plan(this.id).subscribe({
      next: () => this.streamEvents(this.id),
      error: () => {
        this.planning.set(false);
        this.error.set('Не вдалося запустити планування');
      },
    });
  }

  // Поки бекенд не має SSE — опитуємо статус, доки не стане PLANNED (макс ~60с).
  private poll() {
    timer(0, 2000)
      .pipe(
        switchMap(() => this.trips.get(this.id)),
        takeWhile((t) => t.status !== 'PLANNED', true),
        take(30),
      )
      .subscribe({
        next: (t) => {
          this.trip.set(t);
          if (t.status === 'PLANNED') {
            this.planning.set(false);
            this.loadItinerary();
            this.loadContext(t);
          }
        },
        error: () => {
          this.planning.set(false);
          this.error.set('Помилка під час планування');
        },
        complete: () => this.planning.set(false),
      });
  }

  // Реальний час: читаємо SSE-потік статусу через fetch (щоб можна було
  // передати заголовок Authorization, чого EventSource не вміє). Подія 'planned'
  // приходить, коли trip-service застосував маршрут. Якщо потік недоступний —
  // фолбек на поллінг.
  private async streamEvents(id: string): Promise<void> {
    const token = this.auth.token;
    try {
      const res = await fetch(`/api/trips/${id}/plan-events`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok || !res.body) {
        this.poll();
        return;
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      let planned = false;
      for (;;) {
        const { done, value } = await reader.read();
        if (value) {
          buf += decoder.decode(value, { stream: true });
        }
        if (buf.includes('PLANNED')) {
          planned = true;
          break;
        }
        if (done) {
          break;
        }
      }
      reader.cancel().catch(() => undefined);
      if (planned) {
        this.onPlanned(id);
      } else {
        this.poll();
      }
    } catch {
      this.poll();
    }
  }

  private onPlanned(id: string): void {
    this.trips.get(id).subscribe({
      next: (t) => {
        this.trip.set(t);
        this.planning.set(false);
        this.loadItinerary();
        this.loadContext(t);
      },
      error: () => this.planning.set(false),
    });
  }

  private loadItinerary() {
    this.trips.itinerary(this.id).subscribe({ next: (it) => this.itinerary.set(it) });
  }

  private loadContext(t: Trip) {
    this.trips
      .context(t.destinationLat, t.destinationLon, t.startDate, t.endDate)
      .subscribe({ next: (c) => this.ctx.set(c) });
  }

  statusLabel(s: string): string {
    return (
      { DRAFT: 'Чернетка', PLANNING: 'Планується', PLANNED: 'Готово', ARCHIVED: 'Архів' } as Record<string, string>
    )[s] ?? s;
  }

  seasonLabel(s: string): string {
    return (
      { WINTER: 'Зима', SPRING: 'Весна', SUMMER: 'Літо', AUTUMN: 'Осінь' } as Record<string, string>
    )[s] ?? s;
  }
}
