import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TripService } from '../core/trip.service';

@Component({
  selector: 'app-trip-create',
  imports: [FormsModule],
  template: `
    <button class="back" (click)="cancel()">← Скасувати</button>
    <div class="card">
      <h1>Нова подорож</h1>
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
          {{ loading() ? 'Створюємо…' : 'Створити' }}
        </button>
      </form>
    </div>
  `,
})
export class TripCreate {
  private trips = inject(TripService);
  private router = inject(Router);

  m = {
    title: 'Вихідні в Римі',
    destinationName: 'Rome',
    destinationCountry: 'IT',
    destinationLat: 41.9028,
    destinationLon: 12.4964,
    startDate: '',
    endDate: '',
  };
  error = signal<string | null>(null);
  loading = signal(false);

  submit() {
    this.loading.set(true);
    this.error.set(null);
    const req = { ...this.m, destinationCountry: this.m.destinationCountry || undefined };
    this.trips.create(req).subscribe({
      next: (t) => this.router.navigate(['/trips', t.id]),
      error: () => {
        this.error.set('Не вдалося створити подорож');
        this.loading.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/trips']);
  }
}
