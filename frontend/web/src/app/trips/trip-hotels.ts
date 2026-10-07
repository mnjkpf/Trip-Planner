import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { OfferService } from '../core/offer.service';
import { ExpenseRequest, HotelOffer, Trip } from '../core/models';

/**
 * Таб «Готелі» на деталях подорожі. Один виклик при відкритті табу
 * (SerpAPI google_hotels, кеш 6год на бекенді). Фільтри — локальні, у памʼяті.
 */
@Component({
  selector: 'app-trip-hotels',
  imports: [FormsModule, DecimalPipe, TranslatePipe],
  template: `
    <div class="hotels">
      @if (loading()) {
        <div class="loading">{{ 'hotels.loading' | translate }}</div>
      } @else if (error()) {
        <div class="err">{{ error() }}</div>
      } @else if (all().length === 0) {
        <div class="empty">
          {{ 'hotels.empty' | translate: { city: trip().destinationName } }}
        </div>
      } @else {
        <div class="filters">
          <div class="f">
            <label>{{ 'hotels.max_price' | translate }}</label>
            <input type="number" min="0" step="10" [(ngModel)]="maxPricePerNight" />
          </div>
          <div class="f">
            <label>{{ 'hotels.min_stars' | translate }}</label>
            <select [(ngModel)]="minStars">
              <option [ngValue]="0">{{ 'hotels.any' | translate }}</option>
              <option [ngValue]="3">3+</option>
              <option [ngValue]="4">4+</option>
              <option [ngValue]="5">5</option>
            </select>
          </div>
          <div class="f">
            <label>{{ 'hotels.min_rating' | translate }}</label>
            <select [(ngModel)]="minRating">
              <option [ngValue]="0">{{ 'hotels.any' | translate }}</option>
              <option [ngValue]="3.5">3.5+</option>
              <option [ngValue]="4">4+</option>
              <option [ngValue]="4.5">4.5+</option>
            </select>
          </div>
          <div class="f">
            <label>{{ 'hotels.type' | translate }}</label>
            <select [(ngModel)]="typeFilter">
              <option value="">{{ 'hotels.all' | translate }}</option>
              <option value="hotel">{{ 'hotels.hotels_type' | translate }}</option>
              <option value="vacation rental">{{ 'hotels.rentals_type' | translate }}</option>
            </select>
          </div>
          <div class="f-count mono">{{ 'hotels.found' | translate: { n: filtered().length, total: all().length } }}</div>
        </div>

        <div class="list">
          @for (h of filtered(); track h.id ?? h.name) {
            <article class="hotel">
              @if (h.imageUrls.length) {
                <a class="hotel-img" [href]="bookingUrl(h)" target="_blank" rel="noopener"
                   [style.backgroundImage]="'url(' + h.imageUrls[0] + ')'"></a>
              }
              <div class="hotel-body">
                <div class="hotel-head">
                  <a class="hotel-name" [href]="bookingUrl(h)" target="_blank" rel="noopener">{{ h.name }}</a>
                  @if (h.hotelClass) {
                    <span class="stars">{{ '★'.repeat(h.hotelClass) }}</span>
                  }
                  @if (h.type) { <span class="type-chip">{{ h.type }}</span> }
                </div>
                @if (h.rating !== null) {
                  <div class="rating">
                    <b>{{ h.rating }}</b>
                    @if (h.reviewsCount) { <span class="muted">· {{ h.reviewsCount }} {{ 'hotels.reviews' | translate }}</span> }
                  </div>
                }
                @if (h.description) { <p class="hotel-desc">{{ h.description }}</p> }
                @if (h.amenities.length) {
                  <div class="amenities">
                    @for (a of h.amenities.slice(0, 6); track a) {
                      <span class="am-chip">{{ a }}</span>
                    }
                  </div>
                }
              </div>
              <div class="hotel-price">
                @if (h.ratePerNight) {
                  <div class="price-num">{{ h.ratePerNight | number:'1.0-0' }} {{ h.currency }}</div>
                  <div class="price-note">{{ 'hotels.per_night' | translate }}</div>
                } @else if (h.totalRate) {
                  <div class="price-num">{{ h.totalRate | number:'1.0-0' }} {{ h.currency }}</div>
                  <div class="price-note">{{ 'hotels.per_period' | translate }}</div>
                } @else {
                  <div class="price-note">{{ 'hotels.no_price' | translate }}</div>
                }
                @if (h.totalRate || h.ratePerNight) {
                  <button class="btn btn-secondary btn-sm add-budget" (click)="toBudget(h)">
                    {{ 'budget.add_short' | translate }}
                  </button>
                }
                <a class="btn btn-primary btn-sm" [href]="bookingUrl(h)" target="_blank" rel="noopener">
                  {{ h.link ? ('hotels.view' | translate) : ('hotels.find_in_google' | translate) }}
                </a>
              </div>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .hotels { padding: 20px 28px; }
    .loading, .err, .empty { padding: 32px 0; color: var(--muted); font-size: 14px; }
    .err { color: var(--accent-700); font-weight: 600; }

    .filters {
      display: flex; gap: 18px; flex-wrap: wrap; align-items: flex-end;
      padding-bottom: 16px; margin-bottom: 16px; border-bottom: 2px solid var(--divider);
    }
    .f { display: flex; flex-direction: column; gap: 4px; }
    .f label { font-size: 11px; color: var(--muted); letter-spacing: 0.04em; text-transform: uppercase; }
    .f input, .f select {
      min-height: 32px; padding: 4px 8px; font: inherit; font-size: 13px;
      border: 1px solid var(--divider); background: var(--surface);
    }
    .f input { width: 140px; }
    .f-count { margin-left: auto; align-self: flex-end; font-size: 11px; color: var(--muted); }

    .list { display: flex; flex-direction: column; gap: 16px; }
    .hotel {
      display: grid; grid-template-columns: 180px 1fr 180px; gap: 20px;
      padding: 16px; border: 1px solid var(--divider);
    }
    .hotel-img {
      display: block; width: 100%; height: 140px; background: var(--surface) center/cover no-repeat;
    }
    .hotel-head { display: flex; align-items: baseline; gap: 10px; flex-wrap: wrap; }
    .hotel-name { font: 800 16px/1.2 var(--font); color: var(--ink); text-decoration: none; }
    .hotel-name:hover { color: var(--accent); }
    .stars { color: var(--accent); font-size: 14px; }
    .type-chip { background: var(--accent-100); color: var(--accent-800); padding: 2px 8px; font-size: 10px; letter-spacing: 0.04em; text-transform: uppercase; }
    .rating { margin-top: 4px; font-size: 13px; }
    .hotel-desc { margin: 6px 0 0; font-size: 12px; color: var(--muted); line-height: 1.5; max-height: 3em; overflow: hidden; }
    .amenities { display: flex; gap: 6px; margin-top: 8px; flex-wrap: wrap; }
    .am-chip { background: var(--surface); font-size: 10px; padding: 2px 6px; color: var(--muted); }

    .hotel-price {
      border-left: 2px solid var(--divider); padding-left: 20px;
      display: flex; flex-direction: column; align-items: flex-start; justify-content: center; gap: 6px;
    }
    .price-num { font: 800 22px/1 var(--font); color: var(--ink); }
    .price-note { font-size: 10px; letter-spacing: 0.06em; text-transform: uppercase; color: var(--muted); }

    @media (max-width: 720px) {
      .hotel { grid-template-columns: 1fr; }
      .hotel-img { height: 160px; }
      .hotel-price { border-left: 0; border-top: 2px solid var(--divider); padding: 10px 0 0; }
    }
  `],
})
export class TripHotels implements OnInit {
  readonly trip = input.required<Trip>();
  /** Батько пише витрату сам — у нього вже є id подорожі й доступ до сервісу. */
  readonly addToBudget = output<ExpenseRequest>();
  private offers = inject(OfferService);
  private i18n = inject(TranslateService);

  all = signal<HotelOffer[]>([]);
  loading = signal(true);
  error = signal<string | null>(null);

  maxPricePerNight: number | null = null;
  minStars = 0;
  minRating = 0;
  typeFilter = '';

  filtered = computed(() => this.all().filter((h) => {
    if (this.maxPricePerNight && h.ratePerNight && h.ratePerNight > this.maxPricePerNight) return false;
    if (this.minStars && (!h.hotelClass || h.hotelClass < this.minStars)) return false;
    if (this.minRating && (!h.rating || h.rating < this.minRating)) return false;
    if (this.typeFilter && (!h.type || !h.type.toLowerCase().includes(this.typeFilter))) return false;
    return true;
  }));

  /**
   * Прямий лінк з SerpAPI; якщо його нема (SerpAPI часто повертає null для
   * оренди), будуємо пошук Google по назві + місту та датах подорожі.
   */
  /**
   * У бюджет кладемо суму за весь період, якщо вона є; інакше — ціну за ніч,
   * помножену на кількість ночей подорожі. Приблизно, зате не нуль.
   */
  toBudget(h: HotelOffer): void {
    const nights = Math.max(this.nights(), 1);
    const amount = h.totalRate ?? (h.ratePerNight ?? 0) * nights;
    this.addToBudget.emit({
      category: 'HOTEL',
      title: h.name,
      amount: Math.round(amount * 100) / 100,
      currency: h.currency,
      spentOn: null,
      note: h.totalRate ? null : `${h.ratePerNight} × ${nights}`,
    });
  }

  private nights(): number {
    const t = this.trip();
    const ms = new Date(t.endDate).getTime() - new Date(t.startDate).getTime();
    return Math.max(Math.round(ms / 86400000), 1);
  }

  bookingUrl(h: HotelOffer): string {
    if (h.link) return h.link;
    const t = this.trip();
    const q = encodeURIComponent(`${h.name} ${t.destinationName} hotel`);
    return `https://www.google.com/travel/hotels?q=${q}&checkin=${t.startDate}&checkout=${t.endDate}`;
  }

  /** Обовʼязковий input доступний саме тут, не раніше (інакше NG0950). */
  ngOnInit(): void {
    this.load();
  }

  private load(): void {
    const t = this.trip();
    this.offers.hotels(t.destinationName, t.startDate, t.endDate, 2).subscribe({
      next: (res) => {
        this.all.set(res.properties);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.i18n.instant('hotels.error'));
        this.loading.set(false);
      },
    });
  }
}
