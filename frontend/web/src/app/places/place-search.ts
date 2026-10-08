import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { createMap, dotMarker, fit, loadMaps } from '../core/map';
import { AuthService } from '../core/auth.service';
import { PlaceService } from '../core/place.service';
import { WishlistService } from '../core/wishlist.service';
import { Place } from '../core/models';

@Component({
  selector: 'app-place-search',
  imports: [FormsModule, TranslatePipe],
  template: `
    <div class="page places-page">
      <div class="page-head">
        <div class="grow"><div class="kicker">{{ 'places.kicker' | translate }}</div><h1>{{ 'places.title' | translate }}</h1></div>
        <span class="found mono">{{ 'places.found' | translate: {n: results().length, r: radius} }}</span>
      </div>

      <div class="section-soft controls">
        <div class="chips">
          <button class="chip" [class.on]="category === ''" (click)="pickCat('')">{{ 'hotels.all' | translate }}</button>
          @for (c of categories; track c) {
            <button class="chip" [class.on]="category === c" (click)="pickCat(c)">{{ label(c) }}</button>
          }
        </div>
        <div class="controls-r">
          <input class="radius" type="number" [(ngModel)]="radius" name="radius" min="200" max="50000" step="200" />
          <button class="btn btn-secondary btn-sm" (click)="search()" [disabled]="loading()">
            {{ loading() ? ('places.searching' | translate) : ('places.search_here' | translate) }}
          </button>
        </div>
      </div>

      <div class="split">
        <div class="split-map"><div #mapEl class="map-fill"></div></div>
        <div class="split-list">
          @if (loading()) {
            <div class="list-pad muted">{{ 'common.loading' | translate }}</div>
          } @else if (results().length === 0) {
            <div class="list-pad empty">{{ 'places.not_found' | translate }}</div>
          } @else {
            @for (p of results(); track p.id) {
              <div class="place-row">
                <div class="pr-main" (click)="open(p)">
                  <div class="pr-head">
                    <span class="pr-name">{{ p.name }}</span>
                    <span class="tag tag-accent">{{ label(p.category) }}</span>
                  </div>
                  @if (p.description) { <div class="pr-desc">{{ p.description }}</div> }
                  @if (p.address) { <div class="pr-addr mono">{{ p.address }}</div> }
                </div>
                <button
                  class="heart"
                  [class.on]="wishlist.savedIds().has(p.id)"
                  [title]="(wishlist.savedIds().has(p.id) ? 'places.remove_tip' : 'places.save_tip') | translate"
                  (click)="toggleSave(p, $event)"
                >{{ wishlist.savedIds().has(p.id) ? '♥' : '♡' }}</button>
              </div>
            }
          }
        </div>
      </div>
    </div>
  `,
  styles: [`
    .places-page { display: flex; flex-direction: column; flex: 1; min-height: 0; }
    .found { font-size: 11px; color: var(--muted); }
    .controls { display: flex; align-items: center; gap: 14px; flex-wrap: wrap; }
    .chips { display: flex; gap: 0; flex-wrap: wrap; border: 1px solid var(--divider); }
    .chip {
      background: transparent; border: 0; border-right: 1px solid var(--divider-soft);
      padding: 7px 12px; font: 600 12px/1.1 var(--font); color: var(--ink); cursor: pointer;
    }
    .chip:last-child { border-right: 0; }
    .chip:hover:not(.on) { background: rgba(14, 14, 26, 0.06); }
    .chip.on { background: var(--accent); color: var(--bg); }
    .controls-r { margin-left: auto; display: flex; gap: 10px; align-items: center; }
    .radius { width: 110px; min-height: 34px; }

    .split { display: flex; flex-wrap: wrap; flex: 1; min-height: 0; }
    .split-map { flex: 1 1 300px; min-width: 230px; position: relative; min-height: 420px; }
    .map-fill { position: absolute; inset: 0; background: var(--surface); }
    .split-list { flex: 1 1 320px; min-width: 260px; border-left: 2px solid var(--divider); display: flex; flex-direction: column; }
    .list-pad { padding: 20px; }

    .place-row { display: flex; gap: 14px; align-items: flex-start; padding: 14px 20px; border-bottom: 1px solid var(--divider-soft); }
    .place-row:hover { background: rgba(14, 14, 26, 0.04); }
    .pr-main { flex: 1; min-width: 0; cursor: pointer; }
    .pr-head { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
    .pr-name { font: 800 15px/1.2 var(--font); }
    .pr-desc { font-size: 12px; color: var(--muted); margin-top: 4px; line-height: 1.5; }
    .pr-addr { font-size: 11px; color: var(--muted-2); margin-top: 4px; }
    .heart {
      width: 34px; height: 34px; flex: none; border: 1px solid var(--divider); background: #fff;
      color: var(--muted); font-size: 16px; line-height: 1; cursor: pointer;
    }
    .heart:hover { border-color: var(--accent); color: var(--accent); }
    .heart.on { color: var(--accent); border-color: var(--accent); background: var(--accent-100); }
  `],
})
export class PlaceSearch implements AfterViewInit, OnDestroy {
  private places = inject(PlaceService);
  private router = inject(Router);
  protected wishlist = inject(WishlistService);
  protected auth = inject(AuthService);
  private mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');
  private map: google.maps.Map | null = null;
  private markers: google.maps.marker.AdvancedMarkerElement[] = [];
  private clickListener: google.maps.MapsEventListener | null = null;

  results = signal<Place[]>([]);
  loading = signal(false);
  category = '';
  radius = 3000;

  private i18n = inject(TranslateService);
  categories = ['HOTEL','RESTAURANT','CAFE','BAR','MUSEUM','ATTRACTION','PARK','BEACH','SHOP','OTHER'];

  constructor() {
    this.wishlist.load();
  }

  label(c: string): string { return this.i18n.instant('category.' + c) || c; }

  pickCat(c: string): void {
    this.category = c;
    this.search();
  }

  async ngAfterViewInit(): Promise<void> {
    try {
      await loadMaps();
    } catch {
      return;
    }
    this.map = createMap(this.mapEl().nativeElement, { lat: 41.9028, lng: 12.4964 }, 13);
    this.search();
  }

  ngOnDestroy(): void {
    this.clickListener?.remove();
    this.clearMarkers();
  }

  private clearMarkers(): void {
    for (const m of this.markers) m.map = null;
    this.markers = [];
  }

  search(): void {
    if (!this.map) return;
    this.loading.set(true);
    const c = this.map.getCenter();
    if (!c) { this.loading.set(false); return; }
    this.places.search(c.lat(), c.lng(), this.radius, this.category || undefined).subscribe({
      next: (r) => {
        this.results.set(r);
        this.render(r);
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  toggleSave(p: Place, ev: Event): void {
    ev.stopPropagation();
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

  private render(list: Place[]): void {
    if (!this.map) return;
    this.clearMarkers();
    const info = new google.maps.InfoWindow();
    for (const p of list) {
      const m = dotMarker(this.map, { lat: p.lat, lng: p.lon }, p.name);
      m.addListener('click', () => {
        info.setContent('<b>' + this.escape(p.name) + '</b><br>' + this.escape(this.label(p.category)));
        info.open({ map: this.map!, anchor: m });
      });
      this.markers.push(m);
    }
    if (list.length) fit(this.map, list.map((p) => ({ lat: p.lat, lng: p.lon })));
  }

  open(p: Place): void {
    this.router.navigate(['/places', p.id]);
  }

  private escape(s: string): string {
    const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' };
    return s.replace(/[&<>"]/g, (ch) => map[ch]);
  }
}
