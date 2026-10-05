import { DecimalPipe } from '@angular/common';
import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { Airport, ExpenseRequest, FlightOffer, Trip } from '../core/models';
import { OfferService } from '../core/offer.service';
import { PlaceService } from '../core/place.service';

/**
 * Таб «Рейси» на деталях подорожі.
 *
 * UI-процес:
 *  1. Беремо originAirport з trip (якщо вказаний на створенні).
 *  2. Аеропорт прибуття підбираємо автоматично як найближчий до координат
 *     призначення — користувач може перебити вибором іншого.
 *  3. Автоматично запитуємо рейси; фільтри — локально.
 */
@Component({
  selector: 'app-trip-flights',
  imports: [FormsModule, DecimalPipe, TranslatePipe],
  template: `
    <div class="flights">
      <div class="route">
        <div class="field airport">
          <label>{{ 'flights.from' | translate }}</label>
          <input
            name="from"
            autocomplete="off"
            [placeholder]="'flights.placeholder' | translate"
            [(ngModel)]="fromQuery"
            (ngModelChange)="onFromQuery($event)"
            (focus)="fromOpen.set(true)"
          />
          @if (fromOpen() && fromSuggest().length > 0) {
            <div class="dd">
              @for (a of fromSuggest(); track a.iata) {
                <button type="button" class="dd-row" (mousedown)="pickFrom(a)">
                  <span class="iata-chip">{{ a.iata }}</span>{{ a.city }} · {{ a.country }}
                </button>
              }
            </div>
          }
        </div>
        <div class="arrow">→</div>
        <div class="field airport">
          <label>{{ 'flights.to' | translate }}</label>
          <input
            name="to"
            autocomplete="off"
            [placeholder]="'flights.placeholder' | translate"
            [(ngModel)]="toQuery"
            (ngModelChange)="onToQuery($event)"
            (focus)="toOpen.set(true)"
          />
          @if (toOpen() && toSuggest().length > 0) {
            <div class="dd">
              @for (a of toSuggest(); track a.iata) {
                <button type="button" class="dd-row" (mousedown)="pickTo(a)">
                  <span class="iata-chip">{{ a.iata }}</span>{{ a.city }} · {{ a.country }}
                </button>
              }
            </div>
          }
        </div>
        <button class="btn btn-primary btn-sm" (click)="load()" [disabled]="loading() || !canSearch()">
          {{ loading() ? ('flights.searching' | translate) : ('flights.search_btn' | translate) }}
        </button>
      </div>

      @if (error()) { <div class="err">{{ error() }}</div> }

      @if (loaded() && all().length === 0 && !loading()) {
        <div class="empty">
          {{
            'flights.empty'
              | translate
                : {
                    from: from(),
                    to: to(),
                    dates: trip().startDate + ' ↔ ' + trip().endDate
                  }
          }}
        </div>
      } @else if (all().length > 0) {
        <div class="filters">
          <div class="f">
            <label>{{ 'flights.max_price' | translate }}</label>
            <input type="number" min="0" step="10" [(ngModel)]="maxPrice" />
          </div>
          <div class="f">
            <label>{{ 'flights.stops' | translate }}</label>
            <select [(ngModel)]="maxLayovers">
              <option [ngValue]="99">{{ 'flights.any_stops' | translate }}</option>
              <option [ngValue]="0">{{ 'flights.direct' | translate }}</option>
              <option [ngValue]="1">{{ 'flights.max_1_stop' | translate }}</option>
            </select>
          </div>
          <div class="f">
            <label>{{ 'flights.airline' | translate }}</label>
            <select [(ngModel)]="airlineFilter">
              <option value="">{{ 'flights.any_stops' | translate }}</option>
              @for (a of airlines(); track a) { <option [value]="a">{{ a }}</option> }
            </select>
          </div>
          <div class="f-count mono">{{ 'flights.found' | translate: { n: filtered().length, total: all().length } }}</div>
        </div>

        <div class="list">
          @for (f of filtered(); track $index) {
            <article class="flight" [class.flight-best]="isBest(f)">
              <div class="segments">
                @for (s of f.segments; track $index) {
                  <div class="seg">
                    <div class="airline">
                      @if (s.airlineLogoUrl) { <img [src]="s.airlineLogoUrl" alt="" /> }
                      <span>{{ s.airline }} <span class="muted">{{ s.flightNumber }}</span></span>
                    </div>
                    <div class="times mono">
                      <span class="t">{{ shortTime(s.departureTime) }}</span>
                      <span class="code">{{ s.departureAirport }}</span>
                      <span class="dash">— {{ dur(s.durationMinutes) }} —</span>
                      <span class="code">{{ s.arrivalAirport }}</span>
                      <span class="t">{{ shortTime(s.arrivalTime) }}</span>
                    </div>
                  </div>
                }
              </div>
              <div class="meta">
                <div class="dur">{{ dur(f.totalDurationMinutes) }}</div>
                <div class="stops">
                  @if (f.layoverCount === 0) {
                    {{ 'flights.direct_label' | translate }}
                  } @else {
                    {{ 'flights.stops_label' | translate: { n: f.layoverCount } }}
                  }
                </div>
                @if (f.carbonEmissionsGrams) {
                  <div class="carbon">
                    {{ 'flights.carbon' | translate: { n: (f.carbonEmissionsGrams / 1000 | number: '1.0-0') } }}
                  </div>
                }
              </div>
              <div class="price">
                <div class="price-num">{{ f.price | number:'1.0-0' }} {{ f.currency }}</div>
                <div class="price-note">{{ f.type ?? '' }}</div>
                <button class="btn btn-secondary btn-sm book-btn" (click)="toBudget(f)">
                  {{ 'budget.add_short' | translate }}
                </button>
                <a class="btn btn-primary btn-sm book-btn" [href]="bookingUrl()" target="_blank" rel="noopener">
                  {{ 'flights.book' | translate }}
                </a>
              </div>
            </article>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .flights { padding: 20px 28px; }
    .route { display: flex; gap: 14px; align-items: flex-end; flex-wrap: wrap; margin-bottom: 20px; }
    .airport { position: relative; min-width: 220px; }
    .airport input { min-height: 36px; padding: 6px 10px; font: inherit; font-size: 14px;
      border: 1px solid var(--divider); background: var(--surface); width: 100%; }
    .airport label { display: block; font-size: 11px; color: var(--muted); margin-bottom: 4px; letter-spacing: 0.04em; text-transform: uppercase; }
    .arrow { font: 800 24px/1 var(--font); color: var(--accent); align-self: center; margin-top: 20px; }

    .dd {
      position: absolute; top: 100%; left: 0; right: 0; z-index: 400; background: #fff;
      border: 2px solid var(--ink); border-top: 0; max-height: 240px; overflow: auto;
    }
    .dd-row { display: block; width: 100%; text-align: left; background: transparent; border: 0;
      border-bottom: 1px solid var(--divider-soft); padding: 7px 10px; cursor: pointer; font-size: 12px; }
    .dd-row:hover { background: var(--accent-100); }
    .iata-chip { background: var(--ink); color: var(--bg); padding: 1px 6px; font-size: 11px; margin-right: 6px; letter-spacing: 0.04em; }

    .err { color: var(--accent-700); font-weight: 600; margin: 8px 0; }
    .empty { padding: 32px 0; color: var(--muted); font-size: 14px; }

    .filters {
      display: flex; gap: 18px; flex-wrap: wrap; align-items: flex-end;
      padding-bottom: 14px; margin-bottom: 16px; border-bottom: 2px solid var(--divider);
    }
    .f { display: flex; flex-direction: column; gap: 4px; }
    .f label { font-size: 11px; color: var(--muted); letter-spacing: 0.04em; text-transform: uppercase; }
    .f input, .f select { min-height: 32px; padding: 4px 8px; font: inherit; font-size: 13px;
      border: 1px solid var(--divider); background: var(--surface); }
    .f input { width: 120px; }
    .f-count { margin-left: auto; align-self: flex-end; font-size: 11px; color: var(--muted); }

    .list { display: flex; flex-direction: column; gap: 10px; }
    .flight {
      display: grid; grid-template-columns: 1fr 160px 140px; gap: 20px; align-items: center;
      padding: 14px 16px; border: 1px solid var(--divider);
    }
    .flight-best { border-left: 3px solid var(--accent); }
    .segments { display: flex; flex-direction: column; gap: 6px; }
    .seg { display: flex; flex-direction: column; gap: 2px; }
    .airline { display: flex; align-items: center; gap: 8px; font: 800 13px/1.2 var(--font); }
    .airline img { width: 22px; height: 22px; object-fit: contain; background: #fff; padding: 2px; }
    .times { display: flex; align-items: center; gap: 10px; font-size: 13px; }
    .t { font-weight: 800; }
    .code { background: var(--surface); padding: 1px 6px; font-size: 11px; }
    .dash { color: var(--muted); font-size: 11px; }
    .meta { font-size: 12px; color: var(--muted); display: flex; flex-direction: column; gap: 2px; }
    .dur { font: 800 14px/1 var(--font); color: var(--ink); }
    .carbon { font-size: 10px; letter-spacing: 0.04em; color: var(--accent-700); }
    .price { border-left: 2px solid var(--divider); padding-left: 16px; text-align: left; }
    .price-num { font: 800 20px/1 var(--font); color: var(--ink); }
    .price-note { font-size: 10px; letter-spacing: 0.04em; text-transform: uppercase; color: var(--muted); margin-top: 2px; }
    .book-btn { margin-top: 10px; font-size: 11px; padding: 6px 10px; width: 100%; justify-content: center; }

    @media (max-width: 760px) {
      .flight { grid-template-columns: 1fr; }
      .price { border-left: 0; border-top: 2px solid var(--divider); padding: 10px 0 0; }
    }
  `],
})
export class TripFlights implements OnInit {
  readonly trip = input.required<Trip>();
  /** Батько пише витрату сам — у нього вже є id подорожі й доступ до сервісу. */
  readonly addToBudget = output<ExpenseRequest>();
  private offers = inject(OfferService);
  private places = inject(PlaceService);
  private i18n = inject(TranslateService);

  from = signal<string | null>(null);
  to = signal<string | null>(null);
  fromQuery = '';
  toQuery = '';
  fromOpen = signal(false);
  toOpen = signal(false);
  fromSuggest = signal<Airport[]>([]);
  toSuggest = signal<Airport[]>([]);
  private fromQ$ = new Subject<string>();
  private toQ$ = new Subject<string>();

  all = signal<FlightOffer[]>([]);
  bestIds = signal<Set<number>>(new Set());
  loading = signal(false);
  loaded = signal(false);
  error = signal<string | null>(null);

  maxPrice: number | null = null;
  maxLayovers = 99;
  airlineFilter = '';

  airlines = computed(() => {
    const set = new Set<string>();
    for (const f of this.all()) for (const s of f.segments) if (s.airline) set.add(s.airline);
    return [...set].sort();
  });

  filtered = computed(() => this.all().filter((f) => {
    if (this.maxPrice && f.price > this.maxPrice) return false;
    if (this.maxLayovers !== 99 && f.layoverCount > this.maxLayovers) return false;
    if (this.airlineFilter && !f.segments.some((s) => s.airline === this.airlineFilter)) return false;
    return true;
  }));

  canSearch(): boolean {
    return !!this.from() && !!this.to();
  }

  constructor() {
    this.fromQ$
      .pipe(debounceTime(250), distinctUntilChanged(),
        switchMap((q) => q.trim().length < 2 ? of([] as Airport[])
          : this.places.airports(q, 6).pipe(catchError(() => of([] as Airport[])))),
        takeUntilDestroyed())
      .subscribe((l) => this.fromSuggest.set(l));
    this.toQ$
      .pipe(debounceTime(250), distinctUntilChanged(),
        switchMap((q) => q.trim().length < 2 ? of([] as Airport[])
          : this.places.airports(q, 6).pipe(catchError(() => of([] as Airport[])))),
        takeUntilDestroyed())
      .subscribe((l) => this.toSuggest.set(l));

  }

  /** Обовʼязковий input доступний саме тут, не раніше (інакше NG0950). */
  ngOnInit(): void {
    this.init();
  }

  private init(): void {
    const t = this.trip();
    if (t.originAirport) {
      this.from.set(t.originAirport);
      this.fromQuery = t.originAirport;
    }
    // Автопідбір найближчого аеропорту до координат призначення.
    this.places.nearestAirport(t.destinationLat, t.destinationLon, 1).subscribe({
      next: (list) => {
        if (list.length > 0) {
          const a = list[0];
          this.to.set(a.iata);
          this.toQuery = `${a.iata} · ${a.city}`;
          if (this.canSearch()) this.load();
        }
      },
      error: () => {},
    });
  }

  onFromQuery(q: string): void { this.fromOpen.set(true); this.fromQ$.next(q); }
  onToQuery(q: string): void { this.toOpen.set(true); this.toQ$.next(q); }

  pickFrom(a: Airport): void {
    this.from.set(a.iata); this.fromQuery = `${a.iata} · ${a.city}`;
    this.fromSuggest.set([]); this.fromOpen.set(false);
  }
  pickTo(a: Airport): void {
    this.to.set(a.iata); this.toQuery = `${a.iata} · ${a.city}`;
    this.toSuggest.set([]); this.toOpen.set(false);
  }

  load(): void {
    const t = this.trip(); const f = this.from(); const to = this.to();
    if (!f || !to) return;
    this.loading.set(true); this.error.set(null); this.loaded.set(false);
    this.offers.flights(f, to, t.startDate, t.endDate, 1).subscribe({
      next: (res) => {
        this.all.set([...res.best, ...res.other]);
        this.bestIds.set(new Set(res.best.map((_, i) => i)));
        this.loaded.set(true);
        this.loading.set(false);
      },
      error: () => {
        this.error.set(this.i18n.instant('flights.error'));
        this.loading.set(false);
      },
    });
  }

  isBest(f: FlightOffer): boolean {
    // «найкращі» — ті, що були в res.best (ми їх поклали першими). Простий маркер:
    // лежать на індексах 0..best.size.
    const idx = this.all().indexOf(f);
    return this.bestIds().has(idx);
  }

  /** Відкриваємо Google Flights з попередньо заповненими параметрами пошуку. */
  toBudget(f: FlightOffer): void {
    const first = f.segments[0];
    const last = f.segments[f.segments.length - 1];
    this.addToBudget.emit({
      category: 'FLIGHT',
      title: `${first?.departureAirport ?? ''} → ${last?.arrivalAirport ?? ''}`.trim(),
      amount: f.price,
      currency: f.currency,
      spentOn: null,
      note: first?.airline ?? null,
    });
  }

  bookingUrl(): string {
    const t = this.trip();
    const from = this.from() ?? '';
    const to = this.to() ?? '';
    const q = encodeURIComponent(`flights from ${from} to ${to} on ${t.startDate} through ${t.endDate}`);
    return `https://www.google.com/travel/flights?q=${q}&hl=en`;
  }

  dur(mins: number): string {
    const h = Math.floor(mins / 60); const m = mins % 60;
    const hs = this.i18n.instant('flights.hours_short');
    const ms = this.i18n.instant('flights.minutes_short');
    return h > 0 ? `${h}${hs} ${m}${ms}` : `${m}${ms}`;
  }

  shortTime(t: string): string {
    // SerpAPI: "2026-11-10 10:30" → "10:30"
    const parts = t?.split(' ') ?? [];
    return parts.length === 2 ? parts[1] : t;
  }
}
