import { Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { OwnPlacePreview } from '../core/models';
import { PlaceService } from '../core/place.service';
import { WishlistService } from '../core/wishlist.service';

@Component({
  selector: 'app-wishlist-list',
  imports: [RouterLink, DatePipe, FormsModule, TranslatePipe],
  template: `
    <div class="page">
      <div class="page-head">
        <div class="grow"><div class="kicker">{{ 'wishlist.kicker' | translate }}</div><h1>{{ 'wishlist.title' | translate }}</h1></div>
        <a class="btn btn-secondary" routerLink="/places">+ {{ 'wishlist.find_places' | translate }}</a>
      </div>

      <div class="section own-section">
        <div class="own-head">
          <div class="kicker">{{ 'own.kicker' | translate }}</div>
          <h3>{{ 'own.title' | translate }}</h3>
          <p class="hint">
            {{ 'own.hint_prefix' | translate }} <span class="mono">https://maps.app.goo.gl/XYZ</span>) {{ 'own.hint_suffix' | translate }}
          </p>
        </div>
        <div class="own-row">
          <input
            class="own-url"
            type="url"
            [placeholder]="'own.url_placeholder' | translate"
            [(ngModel)]="url"
            name="url"
            (keydown.enter)="resolve()"
          />
          <button class="btn btn-secondary btn-sm" (click)="resolve()" [disabled]="loading() || !url.trim()">
            {{ loading() ? ('own.resolving' | translate) : ('own.preview' | translate) }}
          </button>
        </div>
        @if (error()) { <div class="own-err">{{ error()! | translate }}</div> }
        @if (preview(); as p) {
          <div class="own-preview">
            <div class="own-pv-main">
              <div class="own-pv-name">{{ p.name }}</div>
              <div class="own-pv-coords mono">{{ p.lat.toFixed(5) }}, {{ p.lon.toFixed(5) }}</div>
            </div>
            <div class="own-pv-actions">
              <input class="own-note" [placeholder]="'own.note_placeholder' | translate" [(ngModel)]="note" name="note" />
              <button class="btn btn-primary btn-sm" (click)="add()" [disabled]="adding()">
                {{ adding() ? ('own.adding' | translate) : ('own.add_btn' | translate) }}
              </button>
              <button class="btn btn-ghost btn-sm" (click)="reset()">{{ 'own.cancel' | translate }}</button>
            </div>
          </div>
        }
      </div>

      <div class="section wl-body">
        @if (wishlist.loading()) {
          <p class="muted">{{ 'common.loading' | translate }}</p>
        } @else if (wishlist.items().length === 0) {
          <div class="empty">{{ 'wishlist.empty' | translate }}</div>
        } @else {
          @for (w of wishlist.items(); track w.placeId) {
            <div class="wl-row">
              <div class="wl-main" [routerLink]="['/places', w.placeId]">
                <span class="wl-name">{{ w.placeName }}</span>
                @if (w.placeId.startsWith('custom:')) { <span class="own-chip">{{ 'own.chip' | translate }}</span> }
                @if (w.note) { <div class="wl-note muted">{{ w.note }}</div> }
                <div class="wl-meta mono">{{ 'wishlist.added_on' | translate }} {{ w.createdAt | date: 'd MMM y' }}</div>
              </div>
              <button class="btn btn-ghost btn-sm" (click)="remove(w.placeId)">{{ 'wishlist.remove' | translate }}</button>
            </div>
          }
        }
      </div>
    </div>
  `,
  styles: [`
    .own-section { padding-top: 18px; padding-bottom: 18px; }
    .own-head h3 { font-size: 18px; margin: 4px 0 6px; }
    .own-head .hint { font-size: 12px; color: var(--muted); margin: 0 0 14px; max-width: 640px; line-height: 1.5; }
    .own-head .mono { background: var(--surface); padding: 1px 5px; font-size: 11px; }
    .own-row { display: flex; gap: 10px; max-width: 760px; }
    .own-url { flex: 1; min-height: 38px; padding: 6px 12px; font: inherit; font-size: 13px;
      border: 1px solid var(--divider); background: var(--surface); }
    .own-url:focus { outline: none; border-color: var(--accent); }
    .own-err { margin-top: 10px; color: var(--accent-700); font-size: 13px; font-weight: 600; max-width: 760px; }
    .own-preview {
      margin-top: 14px; display: flex; gap: 20px; align-items: center; flex-wrap: wrap;
      padding: 14px 16px; border: 2px solid var(--accent); background: var(--accent-100);
      max-width: 760px;
    }
    .own-pv-main { flex: 1; min-width: 180px; }
    .own-pv-name { font: 800 15px/1.2 var(--font); color: var(--ink); }
    .own-pv-coords { font-size: 11px; color: var(--muted); margin-top: 4px; }
    .own-pv-actions { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .own-note { min-height: 32px; padding: 4px 10px; font: inherit; font-size: 13px; width: 220px;
      border: 1px solid var(--divider); background: #fff; }

    .wl-body { border-bottom: 0; padding-top: 10px; }
    .wl-row { display: flex; flex-wrap: wrap; gap: 18px; align-items: center; padding: 18px 0; border-bottom: 1px solid var(--divider-soft); }
    .wl-main { flex: 1 1 260px; min-width: 0; cursor: pointer; }
    .wl-name { font: 800 18px/1.2 var(--font); letter-spacing: -0.02em; }
    .own-chip { display: inline-block; margin-left: 8px; background: var(--accent); color: var(--bg);
      font: 800 10px/1 var(--font); letter-spacing: 0.06em; text-transform: uppercase; padding: 2px 6px;
      vertical-align: middle; }
    .wl-note { font-size: 12px; margin-top: 4px; }
    .wl-meta { font-size: 11px; color: var(--muted-2); margin-top: 4px; }
  `],
})
export class WishlistList {
  protected wishlist = inject(WishlistService);
  private places = inject(PlaceService);
  private i18n = inject(TranslateService);

  url = '';
  note = '';
  preview = signal<OwnPlacePreview | null>(null);
  loading = signal(false);
  adding = signal(false);
  error = signal<string | null>(null);

  constructor() {
    this.wishlist.load(true);
  }

  resolve(): void {
    const trimmed = this.url.trim();
    if (!trimmed) return;
    this.loading.set(true); this.error.set(null); this.preview.set(null);
    this.places.resolveLink(trimmed).subscribe({
      next: (p) => { this.preview.set(p); this.loading.set(false); },
      error: (e) => {
        this.loading.set(false);
        const msg = e?.error?.message ?? this.i18n.instant('own.error');
        this.error.set(msg || 'own.error');
      },
    });
  }

  add(): void {
    const p = this.preview(); if (!p) return;
    this.adding.set(true); this.error.set(null);
    // Унікальний placeId для власних місць — нічого не ламає, бо trip-service
    // приймає placeId як string і не намагається його знайти у place-service.
    const placeId = 'custom:' + Date.now().toString(36) + Math.random().toString(36).slice(2, 7);
    this.wishlist.add({
      placeId,
      placeName: p.name,
      placeLat: p.lat,
      placeLon: p.lon,
      note: this.note.trim() || undefined,
    }).subscribe({
      next: () => { this.reset(); this.adding.set(false); },
      error: () => {
        this.adding.set(false);
        this.error.set('own.add_error');
      },
    });
  }

  reset(): void {
    this.url = ''; this.note = '';
    this.preview.set(null);
    this.error.set(null);
  }

  remove(placeId: string): void {
    this.wishlist.remove(placeId).subscribe();
  }
}
