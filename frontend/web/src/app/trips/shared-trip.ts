import { Component, ElementRef, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { clearOverlays, createMap, fit, loadMaps, numberedMarker, routeByRoads, routePolyline } from '../core/map';
import { SharedTrip, SharedTripDay } from '../core/models';
import { TripService } from '../core/trip.service';

/**
 * Маршрут за публічним посиланням (/s/:token). Сторінка гостя: без логіну,
 * без редагування, без вішліста — тільки дні, точки й мапа. Дані бере з
 * /api/public/trips/{token}, який gateway пускає повз JWT.
 */
@Component({
  selector: 'app-shared-trip',
  imports: [DatePipe, TranslatePipe, RouterLink],
  template: `
    @if (trip(); as t) {
      <div class="page shared">
        <div class="sh-head">
          <div class="sh-badge mono">{{ 'share.guest_badge' | translate }}</div>
          <h1 class="sh-title">{{ t.title }}</h1>
          <div class="sh-meta">
            <span>{{ t.destinationName }}</span>
            @if (t.destinationCountry) { <span class="sh-cc">{{ t.destinationCountry }}</span> }
            <span class="sep">·</span>
            <span>{{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}</span>
          </div>
          @if (t.days.length > 0) {
            <div class="sh-export">
              <a class="btn btn-secondary btn-sm" [routerLink]="['/s', token, 'print']" target="_blank">
                {{ 'export.pdf' | translate }}
              </a>
            </div>
          }
        </div>

        @if (t.days.length === 0) {
          <div class="section"><p class="muted">{{ 'share.no_route' | translate }}</p></div>
        } @else {
          <div class="daybar">
            @for (d of t.days; track d.dayIndex) {
              <button class="daytab" [class.on]="d.dayIndex === activeIndex()" (click)="pickDay(d.dayIndex)">
                <span class="dnum">{{ d.dayIndex }}</span>
                <span class="ddate mono">{{ d.date | date: 'd MMM' }}</span>
              </button>
            }
          </div>

          <div class="sh-body">
            <div class="sh-list">
              @if (activeDay(); as d) {
                <div class="dstats mono">
                  {{ d.distanceKm }} km · {{ 'edit.walk_min' | translate: { n: d.walkMinutes } }}
                </div>
                @for (i of d.items; track $index) {
                  <article class="item">
                    <div class="inum">{{ i.order }}</div>
                    @if (i.imageUrl) {
                      <img class="iphoto" [src]="i.imageUrl" [alt]="i.placeName"
                           loading="lazy" (error)="hideImage($event)" />
                    }
                    <div class="ibody">
                      <div class="iname">{{ i.placeName }}</div>
                      <div class="imeta mono">
                        @if (i.time) { <span>{{ i.time }}</span><span class="sep">·</span> }
                        <span>{{ i.dwellMinutes }} {{ 'flights.minutes_short' | translate }}</span>
                        @if (i.placeCategory) {
                          <span class="sep">·</span><span>{{ 'category.' + i.placeCategory | translate }}</span>
                        }
                      </div>
                      @if (i.note) { <div class="inote">{{ i.note }}</div> }
                    </div>
                  </article>
                } @empty {
                  <p class="muted">{{ 'edit.no_items' | translate }}</p>
                }
              }
            </div>
            <div class="sh-map">
              <div #mapEl class="map-fill"></div>
            </div>
          </div>
        }

        @if (t.photos.length > 0) {
          <section class="sh-photos">
            <div class="kicker">{{ 'photos.title' | translate }}</div>
            <div class="sh-grid">
              @for (p of t.photos; track p.url) {
                <!-- Повний розмір відкриваємо в новій вкладці: гостю не потрібен
                     повноцінний переглядач, а сторінка лишається простою. -->
                <a class="sh-photo" [href]="p.url" target="_blank" rel="noopener">
                  <img [src]="p.thumbUrl" [alt]="p.caption ?? p.placeName ?? ''" loading="lazy" />
                  @if (p.placeName || p.caption) {
                    <span class="sh-photo-cap">{{ p.placeName ?? p.caption }}</span>
                  }
                </a>
              }
            </div>
          </section>
        }
      </div>
    } @else if (error()) {
      <div class="page sh-error">
        <div class="kicker">{{ 'share.guest_badge' | translate }}</div>
        <h1 class="sh-title">{{ 'share.invalid_title' | translate }}</h1>
        <p class="muted">{{ 'share.invalid_desc' | translate }}</p>
      </div>
    } @else {
      <div class="page"><p class="muted">{{ 'common.loading' | translate }}</p></div>
    }
  `,
  styles: [`
    /* router-outlet підставляє САМ хост компонента як flex-дитину .auth-main,
       а той — flex-рядок (для залогінених це .main із flex-direction: column, де
       дитина тягнеться сама). Без цього хост стискався до ширини тексту, і мапа
       перелітала під список замість того, щоб стати праворуч. */
    :host { display: flex; flex: 1 1 auto; min-width: 0; min-height: 100vh; }
    .shared { display: flex; flex-direction: column; flex: 1 1 auto; width: 100%; min-width: 0; min-height: 0; }
    .sh-head { padding: 24px 20px 16px; border-bottom: 2px solid var(--divider); }
    .sh-badge {
      display: inline-block; background: var(--ink); color: var(--bg);
      font-size: 10px; letter-spacing: 0.08em; text-transform: uppercase; padding: 3px 8px;
    }
    .sh-title { margin: 10px 0 6px; }
    .sh-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; font-size: 13px; color: var(--muted); }
    .sh-meta .sep { opacity: 0.4; }
    .sh-cc { background: var(--accent-100); color: var(--accent-800); padding: 2px 7px; font-size: 10px; }
    .sh-export { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 12px; }

    .daybar { display: flex; overflow-x: auto; border-bottom: 2px solid var(--divider); }
    .daytab {
      display: flex; flex-direction: column; gap: 3px; align-items: flex-start;
      background: transparent; border: 0; border-right: 2px solid var(--divider);
      padding: 10px 16px; cursor: pointer; flex: none;
    }
    .daytab.on { background: var(--accent); }
    .daytab.on .dnum, .daytab.on .ddate { color: #fff; }
    .dnum { font: 800 15px/1 var(--font); color: var(--ink); }
    .ddate { font-size: 10px; color: var(--muted); text-transform: uppercase; }

    .sh-body { display: flex; flex-wrap: wrap; flex: 1; min-height: 0; }
    .sh-list { flex: 1 1 340px; min-width: 280px; padding: 16px 20px; display: flex; flex-direction: column; gap: 10px; }
    .sh-map { flex: 1 1 380px; min-width: 260px; position: relative; min-height: 420px; border-left: 2px solid var(--divider); }
    .map-fill { position: absolute; inset: 0; background: var(--surface); }

    .dstats { font-size: 11px; color: var(--muted); }
    .item { display: flex; gap: 12px; border: 2px solid var(--divider); padding: 10px 12px; }
    .inum {
      flex: none; width: 24px; height: 24px; display: grid; place-items: center;
      background: var(--accent); color: #fff; font: 800 12px/1 var(--font);
    }
    .iphoto { width: 52px; height: 52px; flex: none; object-fit: cover; border: 1px solid var(--divider); }
    .ibody { min-width: 0; }
    .iname { font: 800 14px/1.25 var(--font); color: var(--ink); }
    .imeta { display: flex; gap: 6px; flex-wrap: wrap; font-size: 11px; color: var(--muted); margin-top: 3px; }
    .imeta .sep { opacity: 0.4; }
    .inote { font-size: 12px; color: var(--muted); margin-top: 5px; }

    .sh-photos { padding: 16px 20px 24px; border-top: 2px solid var(--divider); display: flex; flex-direction: column; gap: 10px; }
    .sh-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
    .sh-photo { position: relative; border: 2px solid var(--divider); background: var(--surface); text-decoration: none; }
    .sh-photo img { display: block; width: 100%; aspect-ratio: 1 / 1; object-fit: cover; }
    .sh-photo-cap {
      position: absolute; left: 0; right: 0; bottom: 0; padding: 5px 7px;
      background: rgba(14, 14, 26, 0.72); color: #fff; font-size: 11px;
      overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
    }
    @media (max-width: 560px) {
      .sh-grid { grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); }
    }

    .sh-error { padding: 40px 20px; }
  `],
})
export class SharedTripView implements OnDestroy {
  private trips = inject(TripService);
  private route = inject(ActivatedRoute);
  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map: google.maps.Map | null = null;
  private overlays: (google.maps.marker.AdvancedMarkerElement | google.maps.Polyline)[] = [];
  private routeToken = 0;
  private mapsReady = false;

  token = this.route.snapshot.paramMap.get('token') ?? '';
  trip = signal<SharedTrip | null>(null);
  error = signal(false);
  activeIndex = signal(1);

  activeDay = computed<SharedTripDay | null>(() => {
    const t = this.trip();
    if (!t) return null;
    return t.days.find((d) => d.dayIndex === this.activeIndex()) ?? t.days[0] ?? null;
  });

  constructor() {
    this.trips.sharedTrip(this.token).subscribe({
      next: (t) => {
        this.trip.set(t);
        if (t.days.length > 0) this.activeIndex.set(t.days[0].dayIndex);
      },
      error: () => this.error.set(true),
    });

    // Контейнер мапи живе всередині @if, тож з'являється лише після відповіді
    // сервера. viewChild — сигнал, тому ефект сам перезапуститься, щойно
    // елемент справді буде в DOM: чекати ngAfterViewInit тут марно.
    effect(() => {
      const t = this.trip();
      const el = this.mapEl()?.nativeElement;
      if (t && el) this.ensureMap(el);
    });
  }

  ngOnDestroy(): void {
    clearOverlays(this.overlays);
    this.overlays = [];
  }

  /** Зовнішнє посилання могло протухнути — тоді просто ховаємо картинку. */
  hideImage(ev: Event): void {
    (ev.target as HTMLImageElement).style.display = 'none';
  }

  pickDay(index: number): void {
    this.activeIndex.set(index);
    this.drawDay();
  }

  /** Створює мапу, щойно є і SDK, і сам контейнер у DOM. */
  private async ensureMap(el: HTMLDivElement): Promise<void> {
    if (this.map && this.map.getDiv() !== el) {
      clearOverlays(this.overlays);
      this.overlays = [];
      this.map = null;
    }
    if (this.map) return;
    if (!this.mapsReady && !(await this.initMaps())) return;
    const t = this.trip();
    if (!t) return;
    this.map = createMap(el, { lat: t.destinationLat, lng: t.destinationLon }, 13);
    this.drawDay();
  }

  private async initMaps(): Promise<boolean> {
    try {
      await loadMaps();
      this.mapsReady = true;
      return true;
    } catch {
      return false;
    }
  }

  private drawDay(): void {
    if (!this.map) return;
    clearOverlays(this.overlays);
    this.overlays = [];
    const day = this.activeDay();
    if (!day || day.items.length === 0) return;
    const pts: google.maps.LatLngLiteral[] = day.items.map((i) => ({ lat: i.lat, lng: i.lon }));
    day.items.forEach((i, idx) => {
      this.overlays.push(numberedMarker(this.map!, pts[idx], i.order, i.placeName));
    });
    fit(this.map, pts, 15);
    if (pts.length > 1) this.drawRoute(pts);
  }

  private async drawRoute(pts: google.maps.LatLngLiteral[]): Promise<void> {
    const token = ++this.routeToken;
    const path = await routeByRoads(pts);
    if (!this.map || token !== this.routeToken) return;
    if (path && path.length > 1) {
      this.overlays.push(
        new google.maps.Polyline({
          map: this.map,
          path,
          strokeColor: '#4318d9',
          strokeOpacity: 0.9,
          strokeWeight: 4,
        }),
      );
    } else {
      this.overlays.push(routePolyline(this.map, pts));
    }
  }
}
