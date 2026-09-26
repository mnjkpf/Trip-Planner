import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TripService } from '../core/trip.service';

@Component({
  selector: 'app-trip-edit',
  imports: [FormsModule],
  template: `
    <button class="back" (click)="cancel()">← Скасувати</button>
    <div class="card">
      <h1>Редагувати подорож</h1>

      @if (loaded()) {
        @if (wasPlanned()) {
          <p class="note">
            Зміна дат або координат призначення скине побудований маршрут — його треба буде
            спланувати заново.
          </p>
        }
        <form (ngSubmit)="submit()" #f="ngForm">
          <label>Назва <input name="title" [(ngModel)]="m.title" required /></label>
          <label>Місто призначення <input name="dest" [(ngModel)]="m.destinationName" required /></label>
          <label>Країна (2 літери) <input name="country" [(ngModel)]="m.destinationCountry" maxlength="2" /></label>
          <div class="row">
            <label>Широта <input type="number" step="any" name="lat" [(ngModel)]="m.destinationLat" required /></label>
            <label>Довгота <input type="number" step="any" name="lon" [(ngModel)]="m.destinationLon" required /></label>
          </div>
          <div class="row">
            <label>Початок <input type="date" name="start" [(ngModel)]="m.startDate" required /></label>
            <label>Кінець <input type="date" name="end" [(ngModel)]="m.endDate" required /></label>
          </div>
          @if (error()) { <p class="error">{{ error() }}</p> }
          <button class="primary" type="submit" [disabled]="loading() || f.invalid">
            {{ loading() ? 'Зберігаємо…' : 'Зберегти' }}
          </button>
        </form>
      } @else if (error()) {
        <p class="error">{{ error() }}</p>
      } @else {
        <p class="muted">Завантаження…</p>
      }
    </div>
  `,
})
export class TripEdit {
  private trips = inject(TripService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);

  id = this.route.snapshot.paramMap.get('id')!;

  m = {
    title: '',
    destinationName: '',
    destinationCountry: '',
    destinationLat: 0,
    destinationLon: 0,
    startDate: '',
    endDate: '',
  };
  loaded = signal(false);
  wasPlanned = signal(false);
  error = signal<string | null>(null);
  loading = signal(false);

  constructor() {
    this.trips.get(this.id).subscribe({
      next: (t) => {
        this.m = {
          title: t.title,
          destinationName: t.destinationName,
          destinationCountry: t.destinationCountry ?? '',
          destinationLat: t.destinationLat,
          destinationLon: t.destinationLon,
          startDate: t.startDate,
          endDate: t.endDate,
        };
        this.wasPlanned.set(t.status === 'PLANNED');
        this.loaded.set(true);
      },
      error: () => this.error.set('Не вдалося завантажити подорож'),
    });
  }

  submit() {
    this.loading.set(true);
    this.error.set(null);
    const req = { ...this.m, destinationCountry: this.m.destinationCountry || undefined };
    this.trips.update(this.id, req).subscribe({
      next: (t) => this.router.navigate(['/trips', t.id]),
      error: (e: HttpErrorResponse) => {
        // ProblemDetail з бекенду несе поле detail (409 при плануванні, 400 при датах)
        this.error.set(e?.error?.detail ?? 'Не вдалося зберегти зміни');
        this.loading.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/trips', this.id]);
  }
}
