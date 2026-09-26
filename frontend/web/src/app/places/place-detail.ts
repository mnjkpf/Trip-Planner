import { Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { DecimalPipe } from '@angular/common';
import * as L from 'leaflet';
import { PlaceService } from '../core/place.service';
import { Place } from '../core/models';

@Component({
  selector: 'app-place-detail',
  imports: [RouterLink, DecimalPipe],
  template: `
    <a class="back" routerLink="/places">← До пошуку</a>

    @if (place(); as p) {
      <div class="card place-detail">
        <span class="badge cat-badge">{{ label(p.category) }}</span>
        <h1>{{ p.name }}</h1>
        @if (p.imageUrl) {
          <img class="hero" [src]="p.imageUrl" [alt]="p.name" />
        }
        @if (p.description) { <p class="lead">{{ p.description }}</p> }
        <dl class="facts">
          @if (p.address) { <div><dt>Адреса</dt><dd>{{ p.address }}</dd></div> }
          @if (p.city) {
            <div><dt>Місто</dt><dd>{{ p.city }}@if (p.countryCode) { , {{ p.countryCode }} }</dd></div>
          }
          <div><dt>Координати</dt><dd>{{ p.lat | number: '1.4-4' }}, {{ p.lon | number: '1.4-4' }}</dd></div>
          @if (p.website) {
            <div><dt>Сайт</dt><dd><a [href]="p.website" target="_blank" rel="noopener">{{ p.website }} ↗</a></dd></div>
          }
        </dl>
      </div>
      <div #mapEl class="map map-sm"></div>
    } @else if (error()) {
      <div class="card empty">Місце не знайдено.</div>
    } @else {
      <p class="muted">Завантаження…</p>
    }
  `,
})
export class PlaceDetail implements OnDestroy {
  private route = inject(ActivatedRoute);
  private placesApi = inject(PlaceService);
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map?: L.Map;

  place = signal<Place | null>(null);
  error = signal(false);

  private readonly labels: Record<string, string> = {
    HOTEL: 'Готель', RESTAURANT: 'Ресторан', CAFE: 'Кафе', BAR: 'Бар',
    MUSEUM: 'Музей', ATTRACTION: 'Памʼятка', PARK: 'Парк', BEACH: 'Пляж',
    SHOP: 'Магазин', OTHER: 'Інше',
  };

  constructor() {
    const id = this.route.snapshot.paramMap.get('id')!;
    this.placesApi.get(id).subscribe({
      next: (p) => {
        this.place.set(p);
        // даємо Angular відрендерити #mapEl (він у @if), потім ініціалізуємо карту
        setTimeout(() => this.initMap(p), 0);
      },
      error: () => this.error.set(true),
    });
  }

  label(c: string): string {
    return this.labels[c] ?? c;
  }

  private initMap(p: Place): void {
    const el = this.mapEl()?.nativeElement;
    if (!el || this.map) {
      return;
    }
    this.map = L.map(el).setView([p.lat, p.lon], 15);
    L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
      attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a> contributors',
      maxZoom: 19,
    }).addTo(this.map);
    L.circleMarker([p.lat, p.lon], {
      radius: 9, color: '#1d4ed8', weight: 2, fillColor: '#3b82f6', fillOpacity: 0.85,
    }).addTo(this.map);
    setTimeout(() => this.map?.invalidateSize(), 0);
  }

  ngOnDestroy(): void {
    this.map?.remove();
  }
}
