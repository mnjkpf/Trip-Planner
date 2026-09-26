import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import * as L from 'leaflet';
import { PlaceService } from '../core/place.service';
import { WishlistService } from '../core/wishlist.service';
import { Place } from '../core/models';

@Component({
  selector: 'app-place-search',
  imports: [FormsModule],
  template: `
    <div class="page-head"><h1>Місця</h1></div>

    <div class="card controls">
      <label>Категорія
        <select [(ngModel)]="category" name="category">
          <option value="">усі</option>
          @for (c of categories; track c) {
            <option [value]="c">{{ label(c) }}</option>
          }
        </select>
      </label>
      <label>Радіус, м
        <input type="number" [(ngModel)]="radius" name="radius" min="200" max="50000" step="200" />
      </label>
      <button class="primary" (click)="search()" [disabled]="loading()">
        {{ loading() ? 'Шукаємо…' : 'Шукати в центрі карти' }}
      </button>
    </div>

    <div #mapEl class="map"></div>

    @if (loading()) {
      <p class="muted">Завантаження…</p>
    } @else if (results().length === 0) {
      <div class="card empty">Нічого не знайдено. Спробуй більший радіус або іншу категорію.</div>
    } @else {
      <div class="grid places-grid">
        @for (p of results(); track p.id) {
          <div class="card place-card" (click)="open(p)">
            <button
              class="save-btn"
              [class.saved]="wishlist.savedIds().has(p.id)"
              [title]="wishlist.savedIds().has(p.id) ? 'Прибрати з вішлісту' : 'Зберегти у вішліст'"
              (click)="toggleSave(p, $event)"
            >{{ wishlist.savedIds().has(p.id) ? '♥' : '♡' }}</button>
            <span class="badge cat-badge">{{ label(p.category) }}</span>
            <h3>{{ p.name }}</h3>
            @if (p.description) { <p class="muted">{{ p.description }}</p> }
            @if (p.address) { <p class="addr">{{ p.address }}</p> }
            @if (p.website) {
              <a class="site" [href]="p.website" target="_blank" rel="noopener" (click)="$event.stopPropagation()">сайт ↗</a>
            }
          </div>
        }
      </div>
    }
  `,
})
export class PlaceSearch implements AfterViewInit, OnDestroy {
  private places = inject(PlaceService);
  private router = inject(Router);
  protected wishlist = inject(WishlistService);
  private mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');
  private map!: L.Map;
  private markers = L.layerGroup();

  results = signal<Place[]>([]);
  loading = signal(false);
  category = '';
  radius = 3000;

  private readonly labels: Record<string, string> = {
    HOTEL: 'Готель',
    RESTAURANT: 'Ресторан',
    CAFE: 'Кафе',
    BAR: 'Бар',
    MUSEUM: 'Музей',
    ATTRACTION: 'Памʼятка',
    PARK: 'Парк',
    BEACH: 'Пляж',
    SHOP: 'Магазин',
    OTHER: 'Інше',
  };
  categories = Object.keys(this.labels);

  constructor() {
    this.wishlist.load(); // прогріваємо кеш вішлісту для сердечок на картках
  }

  label(c: string): string {
    return this.labels[c] ?? c;
  }

  ngAfterViewInit(): void {
    this.map = L.map(this.mapEl().nativeElement).setView([41.9028, 12.4964], 13);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.map);
    this.markers.addTo(this.map);
    // контейнер щойно вставлено — даємо Leaflet перерахувати розмір
    setTimeout(() => this.map.invalidateSize(), 0);
    this.search();
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }

  search(): void {
    this.loading.set(true);
    const c = this.map.getCenter();
    this.places.search(c.lat, c.lng, this.radius, this.category || undefined).subscribe({
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
    if (this.wishlist.savedIds().has(p.id)) {
      this.wishlist.remove(p.id).subscribe();
    } else {
      this.wishlist
        .add({ placeId: p.id, placeName: p.name, placeLat: p.lat, placeLon: p.lon })
        .subscribe();
    }
  }

  private render(list: Place[]): void {
    this.markers.clearLayers();
    for (const p of list) {
      L.circleMarker([p.lat, p.lon], {
        radius: 8,
        color: '#1d4ed8',
        weight: 2,
        fillColor: '#3b82f6',
        fillOpacity: 0.85,
      })
        .bindPopup('<b>' + this.escape(p.name) + '</b><br>' + this.label(p.category))
        .addTo(this.markers);
    }
    if (list.length) {
      this.map.fitBounds(L.latLngBounds(list.map((p) => [p.lat, p.lon] as [number, number])).pad(0.2));
    }
  }

  open(p: Place): void {
    this.router.navigate(['/places', p.id]);
  }

  private escape(s: string): string {
    const map: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' };
    return s.replace(/[&<>"]/g, (ch) => map[ch]);
  }
}
