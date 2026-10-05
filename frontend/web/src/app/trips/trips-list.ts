import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { TripService } from '../core/trip.service';
import { Trip } from '../core/models';

@Component({
  selector: 'app-trips-list',
  imports: [RouterLink, DatePipe, TranslatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow">
          <div class="kicker">{{ 'nav.trips' | translate }}</div>
          <h1>{{ 'trips.title' | translate }}</h1>
        </div>
        <a class="btn btn-primary" routerLink="/trips/new">{{ 'trips.new' | translate }}</a>
      </div>

      <div class="section-soft toolbar">
        <div class="seg">
          @for (f of filters; track f.key) {
            <button [class.on]="filter() === f.key" (click)="filter.set(f.key)">
              {{ f.labelKey | translate }}
            </button>
          }
        </div>
        <span class="count-note mono">{{ filtered().length }} / {{ trips().length }}</span>
      </div>

      @if (loading()) {
        <div class="section"><p class="muted">{{ 'common.loading' | translate }}</p></div>
      } @else if (filtered().length === 0) {
        <div class="section">
          <div class="empty">{{ 'trips.empty' | translate }} <a routerLink="/trips/new">{{ 'trips.new' | translate }}</a></div>
        </div>
      } @else {
        <div class="section tbl">
          <table class="table">
            <thead>
              <tr>
                <th>{{ 'trips.col_trip' | translate }}</th>
                <th>{{ 'trips.col_direction' | translate }}</th>
                <th>{{ 'trips.col_dates' | translate }}</th>
                <th>{{ 'trips.col_status' | translate }}</th>
                <th class="w1"></th>
              </tr>
            </thead>
            <tbody>
              @for (t of filtered(); track t.id) {
                <tr class="clickable" (click)="open(t.id)">
                  <td>
                    <div class="t-title">{{ t.title }}</div>
                    <div class="t-meta">{{ 'trips.created_on' | translate }} {{ t.createdAt | date: 'd MMM y' }}</div>
                  </td>
                  <td>{{ t.destinationName }}@if (t.destinationCountry) { · {{ t.destinationCountry }} }</td>
                  <td class="nowrap">{{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}</td>
                  <td><span class="tag" [class]="'st-' + t.status">{{ statusLabelKey(t.status) | translate }}</span></td>
                  <td class="arrow">→</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
      }
    </div>
  `,
  styles: [`
    .toolbar { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .count-note { margin-left: auto; font-size: 11px; color: var(--muted); }
    .tbl { padding-top: 0; padding-bottom: 28px; border-bottom: 0; }
    .t-title { font-weight: 600; }
    .t-meta { font: 400 11px/1.4 var(--font); color: var(--muted-2); margin-top: 2px; }
    .nowrap { white-space: nowrap; }
    .w1 { width: 1%; }
    .arrow { text-align: right; color: var(--accent); font-weight: 800; }
  `],
})
export class TripsList {
  private tripService = inject(TripService);
  private router = inject(Router);
  trips = signal<Trip[]>([]);
  loading = signal(true);
  filter = signal<string>('ALL');

  // Фільтри — кожен має свій i18n-ключ; переклад через | translate у шаблоні
  filters = [
    { key: 'ALL', labelKey: 'trips.filter_all' },
    { key: 'DRAFT', labelKey: 'trips.filter_drafts' },
    { key: 'PLANNED', labelKey: 'trips.filter_ready' },
    { key: 'ARCHIVED', labelKey: 'trips.filter_archive' },
  ];

  filtered = computed(() => {
    const f = this.filter();
    const all = this.trips();
    return f === 'ALL' ? all : all.filter((t) => t.status === f);
  });

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

  open(id: string) {
    this.router.navigate(['/trips', id]);
  }

  statusLabelKey(s: string): string {
    const m: Record<string, string> = {
      DRAFT: 'trips.status_draft',
      PLANNING: 'trips.status_planning',
      PLANNED: 'trips.status_planned',
      ARCHIVED: 'trips.status_archived',
    };
    return m[s] ?? s;
  }
}
