import { AfterViewInit, Component, ElementRef, OnDestroy, inject, signal, viewChild } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';
import { createMap, dotMarker, loadMaps } from '../core/map';
import { Airport, CitySuggestion, CreateTripRequest, PlanPreferences } from '../core/models';
import { PlanPreferencesEditor, cleanPreferences } from './plan-preferences-editor';
import { PlaceService } from '../core/place.service';
import { TripService } from '../core/trip.service';

@Component({
  selector: 'app-trip-create',
  imports: [FormsModule, TranslatePipe, PlanPreferencesEditor],
  template: `
    <div class="page create-page">
      <div class="page-head">
        <div class="grow">
          <button class="back" (click)="cancel()">{{ 'trips.to_list' | translate }}</button>
          <div class="kicker spc">{{ 'create.kicker' | translate }}</div>
          <h1>{{ 'create.title' | translate }}</h1>
        </div>
      </div>

      <div class="split">
        <div class="split-form">
          <form (ngSubmit)="submit()" class="form-grid">
            <div class="field city">
              <label>{{ 'create.destination' | translate }}</label>
              <input
                name="city"
                autocomplete="off"
                [placeholder]="'create.destination_placeholder' | translate"
                [(ngModel)]="query"
                (ngModelChange)="onQuery($event)"
                (focus)="open.set(true)"
                (blur)="closeSoon()"
              />
              @if (open() && (searching() || suggestions().length > 0 || noMatches())) {
                <div class="dd">
                  @if (searching()) {
                    <div class="dd-note mono">{{ 'create.search_cities' | translate }}</div>
                  } @else if (suggestions().length === 0) {
                    <div class="dd-note mono">{{ 'create.no_cities' | translate }}</div>
                  }
                  @for (s of suggestions(); track $index) {
                    <button type="button" class="dd-row" (mousedown)="pick(s)">
                      <span class="dd-name">{{ s.name }}</span>
                      <span class="dd-sub mono">{{ s.formatted }}</span>
                    </button>
                  }
                </div>
              }
            </div>

            <div class="field city">
              <label>{{ 'create.airport' | translate }}</label>
              <input
                name="airport"
                autocomplete="off"
                [placeholder]="'create.airport_placeholder' | translate"
                [(ngModel)]="airportQuery"
                (ngModelChange)="onAirportQuery($event)"
                (focus)="airportOpen.set(true)"
                (blur)="airportCloseSoon()"
              />
              @if (airportOpen() && airportSuggestions().length > 0) {
                <div class="dd">
                  @for (a of airportSuggestions(); track a.iata) {
                    <button type="button" class="dd-row" (mousedown)="pickAirport(a)">
                      <span class="dd-name"><span class="iata-chip">{{ a.iata }}</span> {{ a.city }} · {{ a.country }}</span>
                      <span class="dd-sub mono">{{ a.name }}</span>
                    </button>
                  }
                </div>
              }
              @if (originAirport()) { <p class="hint">{{ 'create.selected' | translate }}: <b>{{ originAirport() }}</b></p> }
            </div>

            <div class="field">
              <label>{{ 'create.trip_name' | translate }}</label>
              <input name="title" [(ngModel)]="title" (ngModelChange)="titleTouched = true" />
            </div>

            <div class="row">
              <div class="field"><label>{{ 'create.start_date' | translate }}</label><input type="date" name="start" [(ngModel)]="startDate" /></div>
              <div class="field"><label>{{ 'create.end_date' | translate }}</label><input type="date" name="end" [(ngModel)]="endDate" /></div>
            </div>

            <div class="coords mono">
              @if (lat() !== null) {
                <span class="co-dot"></span>{{ lat()!.toFixed(4) }}, {{ lon()!.toFixed(4) }}
                @if (country()) { <span class="co-cc">{{ country() }}</span> }
              } @else {
                {{ 'create.coords_empty' | translate }}
              }
            </div>

            <app-plan-preferences [(value)]="prefs" />

            @if (error()) { <p class="error">{{ error()! | translate }}</p> }

            <button class="btn btn-primary self-start" type="submit" [disabled]="loading() || !canSubmit()">
              {{ loading() ? (phase() | translate) : ('create.submit' | translate) }}
            </button>
            <p class="hint">{{ 'create.hint' | translate }}</p>
          </form>
        </div>

        <div class="split-map">
          <div #mapEl class="map-fill"></div>
          <div class="map-hint mono">{{ 'create.map_hint' | translate }}</div>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .create-page { display: flex; flex-direction: column; flex: 1; min-height: 0; }
    .spc { margin-top: 8px; }

    .split { display: flex; flex-wrap: wrap; flex: 1; min-height: 0; }
    .split-form { flex: 1 1 360px; min-width: 300px; padding: 24px 20px; }
    .split-map { flex: 1 1 380px; min-width: 260px; position: relative; min-height: 420px; border-left: 2px solid var(--divider); }
    .map-fill { position: absolute; inset: 0; background: var(--surface); }
    .map-hint {
      position: absolute; left: 0; bottom: 0; z-index: 500;
      background: var(--ink); color: var(--bg); font-size: 10px; letter-spacing: 0.06em;
      text-transform: uppercase; padding: 5px 10px;
    }

    .form-grid { display: flex; flex-direction: column; gap: 16px; max-width: 480px; }
    .row { display: flex; gap: 12px; }
    .row > .field { flex: 1; min-width: 0; }
    .self-start { align-self: flex-start; }
    .hint { font-size: 11px; color: var(--muted); margin: 0; }

    .city { position: relative; }
    .dd {
      position: absolute; top: 100%; left: 0; right: 0; z-index: 600;
      background: #fff; border: 2px solid var(--ink); border-top: 0;
      max-height: 260px; overflow: auto;
    }
    .dd-note { padding: 10px 12px; font-size: 11px; color: var(--muted); }
    .dd-row {
      display: flex; flex-direction: column; align-items: flex-start; gap: 2px;
      width: 100%; text-align: left; background: transparent; border: 0;
      border-bottom: 1px solid var(--divider-soft); padding: 9px 12px; cursor: pointer;
    }
    .dd-row:last-child { border-bottom: 0; }
    .dd-row:hover { background: var(--accent-100); }
    .dd-name { font: 800 13px/1.2 var(--font); color: var(--ink); }
    .dd-sub { font-size: 10px; color: var(--muted); }

    .coords {
      display: flex; align-items: center; gap: 8px; flex-wrap: wrap;
      font-size: 11px; color: var(--muted); border-left: 2px solid var(--divider); padding-left: 10px;
    }
    .co-dot { width: 8px; height: 8px; background: var(--accent); flex: none; }
    .co-cc { background: var(--accent-100); color: var(--accent-800); padding: 2px 7px; font-size: 10px; }
    .iata-chip { background: var(--ink); color: var(--bg); padding: 1px 6px; font-size: 11px; margin-right: 6px; letter-spacing: 0.04em; }
  `],
})
export class TripCreate implements AfterViewInit, OnDestroy {
  private trips = inject(TripService);
  private i18n = inject(TranslateService);
  private places = inject(PlaceService);
  private router = inject(Router);
  private mapEl = viewChild.required<ElementRef<HTMLDivElement>>('mapEl');
  private map: google.maps.Map | null = null;
  private marker: google.maps.marker.AdvancedMarkerElement | null = null;
  private clickListener: google.maps.MapsEventListener | null = null;
  private queries = new Subject<string>();
  private airportQueries = new Subject<string>();
  private blurTimer: ReturnType<typeof setTimeout> | null = null;
  private airportBlurTimer: ReturnType<typeof setTimeout> | null = null;

  title = '';
  titleTouched = false;
  startDate = this.isoIn(14);
  endDate = this.isoIn(16);
  query = '';

  destName = signal('');
  country = signal('');
  lat = signal<number | null>(null);
  lon = signal<number | null>(null);

  suggestions = signal<CitySuggestion[]>([]);
  searching = signal(false);
  noMatches = signal(false);
  open = signal(false);
  airportQuery = '';
  originAirport = signal<string | null>(null);
  airportSuggestions = signal<Airport[]>([]);
  airportOpen = signal(false);

  prefs = signal<PlanPreferences>({});
  error = signal<string | null>(null);
  loading = signal(false);
  phase = signal('create.submit_creating');

  canSubmit(): boolean {
    return (
      this.title.trim().length > 0 &&
      this.query.trim().length > 0 &&
      this.lat() !== null &&
      this.lon() !== null &&
      !!this.startDate &&
      !!this.endDate &&
      this.originAirport() !== null &&
      this.startDate <= this.endDate
    );
  }

  constructor() {
    this.airportQueries
      .pipe(
        debounceTime(250),
        distinctUntilChanged(),
        switchMap((q) => {
          const t = q.trim();
          if (t.length < 2) return of([] as Airport[]);
          return this.places.airports(t, 6).pipe(catchError(() => of([] as Airport[])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((list) => this.airportSuggestions.set(list));

    this.queries
      .pipe(
        debounceTime(300),
        distinctUntilChanged(),
        switchMap((q) => {
          const t = q.trim();
          if (t.length < 2) {
            this.searching.set(false);
            return of([] as CitySuggestion[]);
          }
          return this.places.geocode(t, 6).pipe(catchError(() => of([] as CitySuggestion[])));
        }),
        takeUntilDestroyed(),
      )
      .subscribe((list) => {
        this.searching.set(false);
        this.suggestions.set(list);
        this.noMatches.set(list.length === 0 && this.query.trim().length >= 2);
      });
  }

  async ngAfterViewInit(): Promise<void> {
    try {
      await loadMaps();
    } catch (ex) {
      this.error.set(this.i18n.instant('create.map_load_error'));
      return;
    }
    this.map = createMap(this.mapEl().nativeElement, { lat: 48.5, lng: 15.0 }, 4);
    this.clickListener = this.map.addListener('click', (e: google.maps.MapMouseEvent) => {
      if (!e.latLng) return;
      this.setPoint(e.latLng.lat(), e.latLng.lng());
      this.country.set('');
      this.open.set(false);
    });
  }

  ngOnDestroy(): void {
    if (this.blurTimer !== null) clearTimeout(this.blurTimer);
    if (this.airportBlurTimer !== null) clearTimeout(this.airportBlurTimer);
    this.clickListener?.remove();
  }

  onQuery(q: string): void {
    this.open.set(true);
    this.noMatches.set(false);
    if (q.trim().length >= 2) {
      this.searching.set(true);
    } else {
      this.searching.set(false);
      this.suggestions.set([]);
    }
    this.queries.next(q);
  }

  closeSoon(): void {
    this.blurTimer = setTimeout(() => this.open.set(false), 150);
  }

  onAirportQuery(q: string): void {
    this.airportOpen.set(true);
    this.airportQueries.next(q);
  }

  airportCloseSoon(): void {
    this.airportBlurTimer = setTimeout(() => this.airportOpen.set(false), 150);
  }

  pickAirport(a: Airport): void {
    this.originAirport.set(a.iata);
    this.airportQuery = `${a.iata} · ${a.city}`;
    this.airportSuggestions.set([]);
    this.airportOpen.set(false);
  }

  pick(s: CitySuggestion): void {
    this.query = s.name;
    this.destName.set(s.name);
    this.country.set(s.countryCode && s.countryCode.length === 2 ? s.countryCode.toUpperCase() : '');
    this.suggestions.set([]);
    this.noMatches.set(false);
    this.open.set(false);
    this.setPoint(s.lat, s.lon, 12);
    if (!this.titleTouched || this.title.trim().length === 0) {
      this.title = this.i18n.instant('create.trip_to') + s.name;
    }
  }

  submit(): void {
    const la = this.lat();
    const lo = this.lon();
    if (!this.canSubmit() || la === null || lo === null) return;
    this.loading.set(true);
    this.phase.set('create.submit_creating');
    this.error.set(null);
    const cc = this.country();
    const req: CreateTripRequest = {
      title: this.title.trim(),
      destinationName: this.destName().trim() || this.query.trim(),
      destinationCountry: cc.length === 2 ? cc : undefined,
      destinationLat: la,
      destinationLon: lo,
      originAirport: this.originAirport() ?? undefined,
      startDate: this.startDate,
      endDate: this.endDate,
      preferences: cleanPreferences(this.prefs()),
    };
    this.trips.create(req).subscribe({
      next: (t) => {
        this.phase.set('create.submit_planning');
        this.trips.plan(t.id).subscribe({
          next: () => this.router.navigate(['/trips', t.id]),
          error: () => this.router.navigate(['/trips', t.id]),
        });
      },
      error: () => {
        this.error.set('create.create_error');
        this.loading.set(false);
      },
    });
  }

  cancel(): void {
    this.router.navigate(['/trips']);
  }

  private setPoint(lat: number, lon: number, zoom?: number): void {
    this.lat.set(lat);
    this.lon.set(lon);
    if (!this.map) return;
    const pos = { lat, lng: lon };
    if (this.marker) {
      this.marker.position = pos;
    } else {
      this.marker = dotMarker(this.map, pos);
    }
    this.map.panTo(pos);
    this.map.setZoom(zoom ?? Math.max(this.map.getZoom() ?? 4, 11));
  }

  private isoIn(days: number): string {
    const d = new Date();
    d.setDate(d.getDate() + days);
    return d.toISOString().slice(0, 10);
  }
}
