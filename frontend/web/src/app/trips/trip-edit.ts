import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { PlanPreferences, UpdateTripRequest } from '../core/models';
import { TripService } from '../core/trip.service';
import { PlanPreferencesEditor, fullPreferences, toEditablePreferences } from './plan-preferences-editor';

@Component({
  selector: 'app-trip-edit',
  imports: [FormsModule, TranslatePipe, PlanPreferencesEditor],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <button class="back" (click)="cancel()">{{ 'edit.cancel' | translate }}</button>
          <div class="kicker spc">{{ 'edit.kicker' | translate }}</div>
          <h1>{{ 'edit.title' | translate }}</h1>
        </div>
      </div>
      <div class="section form-section">
        @if (loaded()) {
          @if (wasPlanned()) {
            <div class="note note-mb">{{ 'edit.replan_note' | translate }}</div>
          }
          <form (ngSubmit)="submit()" #f="ngForm" class="form-grid">
            <div class="field">
              <label>{{ 'edit.name' | translate }}</label>
              <input name="title" [(ngModel)]="m.title" required />
            </div>
            <div class="field">
              <label>{{ 'edit.dest' | translate }}</label>
              <input name="dest" [(ngModel)]="m.destinationName" required />
            </div>
            <div class="row">
              <div class="field cc">
                <label>{{ 'edit.country' | translate }}</label>
                <input name="country" [(ngModel)]="m.destinationCountry" maxlength="2" />
              </div>
              <div class="field cc">
                <label>{{ 'edit.airport' | translate }}</label>
                <input name="airport" [(ngModel)]="m.originAirport" maxlength="3" />
              </div>
            </div>
            <div class="row">
              <div class="field">
                <label>{{ 'edit.lat' | translate }}</label>
                <input type="number" step="any" name="lat" [(ngModel)]="m.destinationLat" required />
              </div>
              <div class="field">
                <label>{{ 'edit.lon' | translate }}</label>
                <input type="number" step="any" name="lon" [(ngModel)]="m.destinationLon" required />
              </div>
            </div>
            <div class="row">
              <div class="field">
                <label>{{ 'edit.start' | translate }}</label>
                <input type="date" name="start" [(ngModel)]="m.startDate" required />
              </div>
              <div class="field">
                <label>{{ 'edit.end' | translate }}</label>
                <input type="date" name="end" [(ngModel)]="m.endDate" required />
              </div>
            </div>

            <app-plan-preferences [(value)]="prefs" />

            @if (error()) { <p class="error">{{ error() }}</p> }
            <button class="btn btn-primary self-start" type="submit" [disabled]="loading() || f.invalid">
              {{ loading() ? ('edit.saving' | translate) : ('edit.save' | translate) }}
            </button>
          </form>
        } @else if (error()) {
          <p class="error">{{ error() }}</p>
        } @else {
          <p class="muted">{{ 'common.loading' | translate }}</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .spc { margin-top: 8px; }
    .form-section { max-width: 540px; border-bottom: 0; }
    .form-grid { display: flex; flex-direction: column; gap: 16px; }
    .row { display: flex; gap: 12px; }
    .row > .field { flex: 1; min-width: 0; }
    .cc { max-width: 180px; }
    .self-start { align-self: flex-start; }
    .note-mb { margin-bottom: 16px; }
  `],
})
export class TripEdit {
  private trips = inject(TripService);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  private i18n = inject(TranslateService);

  id = this.route.snapshot.paramMap.get('id')!;

  m = {
    title: '',
    destinationName: '',
    destinationCountry: '',
    originAirport: '',
    destinationLat: 0,
    destinationLon: 0,
    startDate: '',
    endDate: '',
  };
  prefs = signal<PlanPreferences>({});
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
          originAirport: t.originAirport ?? '',
          destinationLat: t.destinationLat,
          destinationLon: t.destinationLon,
          startDate: t.startDate,
          endDate: t.endDate,
        };
        this.prefs.set(toEditablePreferences(t.preferences));
        this.wasPlanned.set(t.status === 'PLANNED');
        this.loaded.set(true);
      },
      error: () => this.error.set(this.i18n.instant('edit.load_error')),
    });
  }

  submit() {
    this.loading.set(true);
    this.error.set(null);
    const airport = this.m.originAirport.trim().toUpperCase();
    const req: UpdateTripRequest = {
      ...this.m,
      destinationCountry: this.m.destinationCountry || undefined,
      originAirport: airport.length === 3 ? airport : undefined,
      preferences: fullPreferences(this.prefs()),
    };
    this.trips.update(this.id, req).subscribe({
      next: (t) => this.router.navigate(['/trips', t.id]),
      error: (e: HttpErrorResponse) => {
        // Текст від бекенда не показуємо — він одномовний; мапимо за статусом.
        this.error.set(this.i18n.instant(e?.status === 409 ? 'edit.conflict' : 'edit.save_error'));
        this.loading.set(false);
      },
    });
  }

  cancel() {
    this.router.navigate(['/trips', this.id]);
  }
}
