import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { createMap, dotMarker, loadMaps } from '../core/map';
import { AuthService } from '../core/auth.service';
import { PlaceService } from '../core/place.service';
import { WishlistService } from '../core/wishlist.service';
import { Place } from '../core/models';

@Component({
  selector: 'app-place-detail',
  imports: [RouterLink, DecimalPipe, TranslatePipe],
  template: `
    <div class="page">
      <div class="pd-head">
        <a class="back" routerLink="/places">{{ 'places.back_to_search' | translate }}</a>
        @if (place(); as p) {
          <div class="pd-head-row">
            <div class="grow">
              <span class="tag tag-accent">{{ label(p.category) }}</span>
              <h1 class="pd-title">{{ p.name }}</h1>
            </div>
            <div class="pd-actions">
              @if (wishlist.savedIds().has(p.id)) {
                <button class="btn btn-danger btn-sm" (click)="toggleSave(p)">{{ 'places.in_wishlist' | translate }}</button>
              } @else {
                <button class="btn btn-secondary btn-sm" (click)="toggleSave(p)">{{ 'places.save' | translate }}</button>
              }
            </div>
          </div>
        }
      </div>

      @if (place(); as p) {
        <div class="pd-body">
          <div class="pd-left">
            @if (p.imageUrl) { <img class="pd-hero" [src]="p.imageUrl" [alt]="p.name" /> }
            @if (p.description) { <p class="pd-lead">{{ p.description }}</p> }
            <table class="table pd-facts">
              <tbody>
                @if (p.address) { <tr><th>{{ 'places.address' | translate }}</th><td>{{ p.address }}</td></tr> }
                @if (p.city) { <tr><th>{{ 'places.city' | translate }}</th><td>{{ p.city }}@if (p.countryCode) { , {{ p.countryCode }} }</td></tr> }
                <tr><th>{{ 'places.coords' | translate }}</th><td class="mono">{{ p.lat | number: '1.4-4' }}, {{ p.lon | number: '1.4-4' }}</td></tr>
                @if (p.website) { <tr><th>{{ 'places.website' | translate }}</th><td><a [href]="p.website" target="_blank" rel="noopener">{{ p.website }} ↗</a></td></tr> }
              </tbody>
            </table>
            <div class="box pd-provider">
              <div class="box-title accent">{{ 'places.source' | translate }}</div>
              <div class="mono pd-pline">{{ 'places.source_line' | translate }}</div>
            </div>
          </div>
          <div class="pd-right"><div #mapEl class="map-fill-r"></div></div>
        </div>
      } @else if (error()) {
        <div class="section"><div class="empty">{{ 'places.not_found_full' | translate }}</div></div>
      } @else {
        <div class="section"><p class="muted">{{ 'common.loading' | translate }}</p></div>
      }
    </div>
  `,
  styles: [`
    .pd-head { padding: 20px 28px 16px; border-bottom: 2px solid var(--divider); }
    .pd-head-row { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; margin-top: 10px; }
    .pd-title { margin: 8px 0 0; }
    .pd-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .pd-body { display: flex; flex-wrap: wrap; }
    .pd-left { flex: 1 1 380px; min-width: 300px; padding: 24px 28px; border-right: 2px solid var(--divider); }
    .pd-hero { width: 100%; max-height: 300px; object-fit: cover; margin-bottom: 18px; filter: grayscale(0.1); }
    .pd-lead { font-size: 16px; line-height: 1.6; margin: 0 0 20px; max-width: 52ch; }
    .pd-facts th { text-transform: none; letter-spacing: 0; font-weight: 600; color: var(--muted); width: 34%; }
    .pd-facts a { word-break: break-all; }
    .pd-provider { margin-top: 20px; }
    .box-title.accent { color: var(--accent); }
    .pd-pline { font-size: 11px; color: var(--muted); margin-top: 6px; }
    .pd-right { flex: 1 1 300px; min-width: 280px; display: flex; }
    .map-fill-r { flex: 1; min-height: 340px; background: var(--surface); }
  `],
})
export class PlaceDetail implements OnDestroy {
  private route = inject(ActivatedRoute);
  private placesApi = inject(PlaceService);
  protected wishlist = inject(WishlistService);
  protected auth = inject(AuthService);
  private router = inject(Router);
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map: google.maps.Map | null = null;

  place = signal<Place | null>(null);
  error = signal(false);

  private i18n = inject(TranslateService);

  constructor() {
    this.wishlist.load();
    const id = this.route.snapshot.paramMap.get('id')!;
    this.placesApi.get(id).subscribe({
      next: (p) => {
        this.place.set(p);
        setTimeout(() => this.initMap(p), 0);
      },
      error: () => this.error.set(true),
    });
  }

  label(c: string): string { return this.i18n.instant('category.' + c) || c; }

  toggleSave(p: Place): void {
    // Гість не має вішлісту — відправляємо на логін замість 401.
    if (!this.auth.isLoggedIn()) {
      this.router.navigate(['/login']);
      return;
    }
    if (this.wishlist.savedIds().has(p.id)) {
      this.wishlist.remove(p.id).subscribe();
    } else {
      this.wishlist.add({ placeId: p.id, placeName: p.name, placeLat: p.lat, placeLon: p.lon }).subscribe();
    }
  }

  private async initMap(p: Place): Promise<void> {
    const el = this.mapEl()?.nativeElement;
    if (!el || this.map) return;
    try {
      await loadMaps();
    } catch {
      return;
    }
    this.map = createMap(el, { lat: p.lat, lng: p.lon }, 15);
    dotMarker(this.map, { lat: p.lat, lng: p.lon }, p.name);
  }

  ngOnDestroy(): void {
    /* google.maps сам прибирає ресурси, коли вузол зникає з DOM */
  }
}
