import { Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TripService } from '../core/trip.service';
import { Trip } from '../core/models';

@Component({
  selector: 'app-trips-list',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="page-head">
      <h1>Мої подорожі</h1>
      <a class="primary" routerLink="/trips/new">+ Нова подорож</a>
    </div>

    @if (loading()) {
      <p class="muted">Завантаження…</p>
    } @else if (trips().length === 0) {
      <div class="card empty">Ще немає подорожей. Створи першу!</div>
    } @else {
      <div class="grid">
        @for (t of trips(); track t.id) {
          <a class="card trip-card" [routerLink]="['/trips', t.id]">
            <span class="badge" [class]="'st-' + t.status">{{ statusLabel(t.status) }}</span>
            <h3>{{ t.title }}</h3>
            <p class="muted">
              {{ t.destinationName }}@if (t.destinationCountry) { · {{ t.destinationCountry }} }
            </p>
            <p class="dates">{{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}</p>
          </a>
        }
      </div>
    }
  `,
})
export class TripsList {
  private tripService = inject(TripService);
  trips = signal<Trip[]>([]);
  loading = signal(true);

  constructor() {
    this.reload();
  }

  reload() {
    this.loading.set(true);
    this.tripService.list().subscribe({
      next: (t) => {
        this.trips.set(t);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  statusLabel(s: string): string {
    return (
      { DRAFT: 'Чернетка', PLANNING: 'Планується', PLANNED: 'Готово', ARCHIVED: 'Архів' } as Record<string, string>
    )[s] ?? s;
  }
}
