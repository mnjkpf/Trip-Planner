import { Component, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { ActivatedRoute } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { forkJoin } from 'rxjs';
import { loadMaps, routeByRoads, staticMapUrl } from '../core/map';
import { TripService } from '../core/trip.service';

interface PrintItem {
  order: number;
  name: string;
  category: string | null;
  time: string | null;
  dwellMinutes: number;
  note: string | null;
  lat: number;
  lon: number;
}

interface PrintDay {
  dayIndex: number;
  date: string;
  distanceKm: number;
  walkMinutes: number;
  items: PrintItem[];
}

interface PrintTrip {
  title: string;
  destinationName: string;
  destinationCountry: string | null;
  startDate: string;
  endDate: string;
  days: PrintDay[];
}

/** Позиція блока в календарній сітці — у відсотках висоти колонки. */
interface GridBlock {
  item: PrintItem;
  topPct: number;
  heightPct: number;
}

interface GridColumn {
  day: PrintDay;
  blocks: GridBlock[];
  untimed: PrintItem[];
}

/** Скільки днів вміщається в одну сітку, щоб колонки лишались читабельними. */
const DAYS_PER_GRID = 7;
const MIN_GRID_MINUTES = 8 * 60;

/**
 * Версія маршруту для друку / «Зберегти як PDF». Один компонент на два входи:
 *   /trips/:id/print — власник (подорож + маршрут за токеном)
 *   /s/:token/print  — гість за публічним посиланням
 *
 * Структура PDF: календарна сітка на всю подорож (огляд) → далі сторінка на
 * кожен день зі статичною мапою й розкладом.
 *
 * PDF робить сам браузер: рідний діалог друку дає коректні шрифти, переноси
 * сторінок і будь-яку мову інтерфейсу без жодної залежності на бекенді.
 */
@Component({
  selector: 'app-trip-print',
  imports: [DatePipe, TranslatePipe],
  template: `
    @if (trip(); as t) {
      <div class="sheet">
        <div class="tools no-print">
          <button class="btn btn-primary btn-sm" (click)="print()" [disabled]="!ready()">
            {{ ready() ? ('export.print_btn' | translate) : ('export.preparing' | translate) }}
          </button>
          <span class="tools-hint">{{ 'export.print_hint' | translate }}</span>
        </div>

        <header class="head">
          <div class="brand">WAY<span>/</span>LO</div>
          <h1>{{ t.title }}</h1>
          <div class="meta">
            {{ t.destinationName }}@if (t.destinationCountry) { , {{ t.destinationCountry }} }
            · {{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}
          </div>
        </header>

        <!-- 1. Огляд: календарна сітка -->
        @for (grid of grids(); track $index) {
          <section class="cal">
            <div class="cal-head">
              <div class="cal-gutter"></div>
              @for (c of grid; track c.day.dayIndex) {
                <div class="cal-col-head">
                  <div class="cal-day">{{ 'detail.day_label' | translate }} {{ c.day.dayIndex }}</div>
                  <div class="cal-date">{{ c.day.date | date: 'EEE, d MMM' }}</div>
                </div>
              }
            </div>
            <div class="cal-body" [style.height.px]="gridHeightPx()">
              <div class="cal-gutter hours">
                @for (h of hours(); track h) {
                  <div class="hour" [style.height.px]="hourPx">{{ h }}</div>
                }
              </div>
              @for (c of grid; track c.day.dayIndex) {
                <div class="cal-col">
                  @for (h of hours(); track h) { <div class="hline" [style.height.px]="hourPx"></div> }
                  @for (b of c.blocks; track $index) {
                    <div class="block" [style.top.%]="b.topPct" [style.height.%]="b.heightPct">
                      <span class="b-num">{{ b.item.order }}</span>
                      <span class="b-time">{{ b.item.time }}</span>
                      <span class="b-name">{{ b.item.name }}</span>
                    </div>
                  }
                </div>
              }
            </div>
            @if (hasUntimed(grid)) {
              <div class="cal-untimed">
                @for (c of grid; track c.day.dayIndex) {
                  @for (i of c.untimed; track $index) {
                    <span class="u-chip">{{ 'detail.day_label' | translate }} {{ c.day.dayIndex }}: {{ i.name }}</span>
                  }
                }
              </div>
            }
          </section>
        }

        <!-- 2. Деталі по днях -->
        @for (d of t.days; track d.dayIndex) {
          <section class="day">
            <h2>
              {{ 'detail.day_label' | translate }} {{ d.dayIndex }}
              <span class="day-date">{{ d.date | date: 'EEEE, d MMMM' }}</span>
            </h2>
            @if (d.items.length > 0) {
              <div class="day-stats">
                {{ d.distanceKm }} km · {{ 'edit.walk_min' | translate: { n: d.walkMinutes } }}
              </div>
              @if (mapUrls()[d.dayIndex]; as url) {
                <img class="day-map" [src]="url" alt="" (error)="mapFailed(d.dayIndex)" />
              }
              <table class="items">
                <tbody>
                  @for (i of d.items; track $index) {
                    <tr>
                      <td class="c-num">{{ i.order }}</td>
                      <td class="c-time">{{ i.time ?? '—' }}</td>
                      <td class="c-name">
                        <div class="n">{{ i.name }}</div>
                        @if (i.category) { <div class="c">{{ 'category.' + i.category | translate }}</div> }
                        @if (i.note) { <div class="note">{{ i.note }}</div> }
                      </td>
                      <td class="c-dwell">{{ i.dwellMinutes }} {{ 'flights.minutes_short' | translate }}</td>
                    </tr>
                  }
                </tbody>
              </table>
            } @else {
              <p class="empty">{{ 'edit.no_items' | translate }}</p>
            }
          </section>
        } @empty {
          <p class="empty">{{ 'share.no_route' | translate }}</p>
        }
      </div>
    } @else if (error()) {
      <p class="empty pad">{{ 'share.invalid_desc' | translate }}</p>
    } @else {
      <p class="empty pad">{{ 'common.loading' | translate }}</p>
    }
  `,
  styles: [`
    :host { display: block; background: #fff; }
    .sheet { max-width: 760px; margin: 0 auto; padding: 32px 24px 60px; color: #11111a; }
    .pad { padding: 32px 24px; }

    .tools { display: flex; align-items: center; gap: 12px; margin-bottom: 24px; }
    .tools-hint { font-size: 11px; color: #6b6b7b; }

    .head { border-bottom: 2px solid #11111a; padding-bottom: 14px; margin-bottom: 20px; }
    .brand { font: 800 12px/1 var(--font); letter-spacing: 0.1em; }
    .brand span { color: #4318d9; }
    .head h1 { font: 800 26px/1.15 var(--font); margin: 10px 0 6px; }
    .meta { font-size: 13px; color: #4a4a58; }

    /* ── календарна сітка ── */
    .cal { margin-bottom: 26px; page-break-inside: avoid; break-inside: avoid; }
    .cal-head { display: flex; border-bottom: 1px solid #11111a; }
    .cal-gutter { width: 34px; flex: none; }
    .cal-col-head { flex: 1 1 0; min-width: 0; padding: 0 4px 5px; border-left: 1px solid #ececf3; }
    .cal-day { font: 800 11px/1.2 var(--font); }
    .cal-date { font-size: 9px; color: #6b6b7b; text-transform: capitalize; }

    .cal-body { display: flex; position: relative; }
    .hours { display: flex; flex-direction: column; }
    .hour { font: 400 8px/1 var(--font); color: #9a9aa8; padding-top: 1px; text-align: right; padding-right: 4px; }
    .cal-col { flex: 1 1 0; min-width: 0; position: relative; border-left: 1px solid #ececf3; }
    .hline { border-top: 1px solid #f2f2f7; }
    .block {
      position: absolute; left: 1px; right: 1px; overflow: hidden;
      background: #efe9ff; border-left: 3px solid #4318d9;
      padding: 1px 3px; font: 600 8px/1.25 var(--font); min-height: 12px;
    }
    .b-num { font-weight: 800; color: #4318d9; margin-right: 3px; }
    .b-time { color: #6b6b7b; margin-right: 3px; }
    .b-name { }
    .cal-untimed { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 6px; }
    .u-chip { font-size: 9px; color: #4a4a58; background: #f4f4f9; padding: 2px 6px; }

    /* ── деталі дня ── */
    .day { margin-bottom: 22px; page-break-inside: avoid; break-inside: avoid; }
    .day h2 {
      font: 800 15px/1.2 var(--font); margin: 0 0 4px;
      display: flex; align-items: baseline; gap: 10px;
      border-bottom: 1px solid #d8d8e2; padding-bottom: 5px;
    }
    .day-date { font: 400 12px/1.2 var(--font); color: #6b6b7b; text-transform: capitalize; }
    .day-stats { font-size: 11px; color: #6b6b7b; margin: 6px 0 8px; }
    .day-map { display: block; width: 100%; height: auto; border: 1px solid #d8d8e2; margin-bottom: 10px; }

    .items { width: 100%; border-collapse: collapse; }
    .items td { padding: 7px 6px; border-bottom: 1px solid #ececf3; vertical-align: top; }
    .c-num { width: 24px; font: 800 12px/1.4 var(--font); color: #4318d9; }
    .c-time { width: 56px; font: 700 12px/1.4 var(--font); white-space: nowrap; }
    .c-name .n { font: 700 13px/1.3 var(--font); }
    .c-name .c { font-size: 11px; color: #6b6b7b; margin-top: 2px; }
    .c-name .note { font-size: 11px; color: #4a4a58; margin-top: 3px; font-style: italic; }
    .c-dwell { width: 64px; text-align: right; font-size: 11px; color: #6b6b7b; white-space: nowrap; }

    .empty { font-size: 12px; color: #6b6b7b; }

    @media print {
      .no-print { display: none !important; }
      .sheet { max-width: none; margin: 0; padding: 0; }
      /* без цього Chrome друкує плашки сітки й мапи білими */
      .block, .u-chip, .day-map { -webkit-print-color-adjust: exact; print-color-adjust: exact; }
      @page { margin: 14mm 12mm; }
    }
  `],
})
export class TripPrint {
  private trips = inject(TripService);
  private route = inject(ActivatedRoute);

  readonly hourPx = 26;

  trip = signal<PrintTrip | null>(null);
  error = signal(false);
  /** Мапи вантажаться асинхронно — друкувати раніше означає PDF без них. */
  ready = signal(false);
  mapUrls = signal<Record<number, string>>({});

  /** Межі сітки в хвилинах від півночі — спільні для всіх днів, щоб колонки збігались. */
  private span = computed(() => {
    const t = this.trip();
    let min = 24 * 60;
    let max = 0;
    for (const d of t?.days ?? []) {
      for (const i of d.items) {
        const start = toMinutes(i.time);
        if (start === null) continue;
        min = Math.min(min, start);
        max = Math.max(max, start + Math.max(i.dwellMinutes, 30));
      }
    }
    if (min >= max) return { from: 9 * 60, to: 9 * 60 + MIN_GRID_MINUTES };
    const from = Math.floor(min / 60) * 60;
    const to = Math.ceil(max / 60) * 60;
    return { from, to: Math.max(to, from + MIN_GRID_MINUTES) };
  });

  hours = computed(() => {
    const { from, to } = this.span();
    const out: string[] = [];
    for (let m = from; m < to; m += 60) out.push(String(Math.floor(m / 60)).padStart(2, '0'));
    return out;
  });

  gridHeightPx = computed(() => this.hours().length * this.hourPx);

  /** Дні розбиті на блоки по DAYS_PER_GRID — довга подорож не стискає колонки в нитку. */
  grids = computed<GridColumn[][]>(() => {
    const t = this.trip();
    if (!t) return [];
    const { from, to } = this.span();
    const total = to - from;

    const columns: GridColumn[] = t.days.map((day) => {
      const blocks: GridBlock[] = [];
      const untimed: PrintItem[] = [];
      for (const item of day.items) {
        const start = toMinutes(item.time);
        if (start === null) {
          untimed.push(item);
          continue;
        }
        const minutes = Math.max(item.dwellMinutes, 20);
        blocks.push({
          item,
          topPct: ((start - from) / total) * 100,
          heightPct: Math.min((minutes / total) * 100, 100 - ((start - from) / total) * 100),
        });
      }
      return { day, blocks, untimed };
    });

    const out: GridColumn[][] = [];
    for (let i = 0; i < columns.length; i += DAYS_PER_GRID) {
      out.push(columns.slice(i, i + DAYS_PER_GRID));
    }
    return out;
  });

  constructor() {
    const token = this.route.snapshot.paramMap.get('token');
    const id = this.route.snapshot.paramMap.get('id');
    if (token) {
      this.loadShared(token);
    } else if (id) {
      this.loadOwned(id);
    } else {
      this.error.set(true);
    }
  }

  print(): void {
    window.print();
  }

  hasUntimed(grid: GridColumn[]): boolean {
    return grid.some((c) => c.untimed.length > 0);
  }

  /** Статична мапа не завантажилась (найчастіше — не ввімкнено Maps Static API). */
  mapFailed(dayIndex: number): void {
    this.mapUrls.update((m) => {
      const next = { ...m };
      delete next[dayIndex];
      return next;
    });
  }

  private loadShared(token: string): void {
    this.trips.sharedTrip(token).subscribe({
      next: (s) =>
        this.ready_({
          title: s.title,
          destinationName: s.destinationName,
          destinationCountry: s.destinationCountry,
          startDate: s.startDate,
          endDate: s.endDate,
          days: s.days.map((d) => ({
            dayIndex: d.dayIndex,
            date: d.date,
            distanceKm: d.distanceKm,
            walkMinutes: d.walkMinutes,
            items: d.items.map((i) => ({
              order: i.order,
              name: i.placeName,
              category: i.placeCategory,
              time: i.time,
              dwellMinutes: i.dwellMinutes,
              note: i.note,
              lat: i.lat,
              lon: i.lon,
            })),
          })),
        }),
      error: () => this.error.set(true),
    });
  }

  private loadOwned(id: string): void {
    forkJoin({ trip: this.trips.get(id), itinerary: this.trips.itinerary(id) }).subscribe({
      next: ({ trip, itinerary }) =>
        this.ready_({
          title: trip.title,
          destinationName: trip.destinationName,
          destinationCountry: trip.destinationCountry,
          startDate: trip.startDate,
          endDate: trip.endDate,
          days: itinerary.days.map((d) => ({
            dayIndex: d.dayIndex,
            date: d.date,
            distanceKm: d.distanceKm,
            walkMinutes: d.walkMinutes,
            items: d.items.map((i) => ({
              order: i.order,
              name: i.placeName,
              category: i.placeCategory,
              time: i.time,
              dwellMinutes: i.dwellMinutes,
              note: i.note,
              lat: i.lat,
              lon: i.lon,
            })),
          })),
        }),
      error: () => this.error.set(true),
    });
  }

  private ready_(t: PrintTrip): void {
    this.trip.set(t);
    this.buildMaps(t);
  }

  /**
   * Готує по статичній мапі на день і лише потім відкриває діалог друку —
   * інакше в PDF потрапили б порожні рамки. Геометрію доріг беремо тим самим
   * Directions, що й інтерактивна мапа; якщо він відмовив, лишається пряма.
   * Якщо щось пішло не так — все одно друкуємо, просто без мап.
   */
  private async buildMaps(t: PrintTrip): Promise<void> {
    try {
      await loadMaps();
      const urls: Record<number, string> = {};
      for (const day of t.days) {
        if (day.items.length === 0) continue;
        const stops = day.items.map((i) => ({ lat: i.lat, lng: i.lon }));
        const path = stops.length > 1 ? await routeByRoads(stops) : null;
        urls[day.dayIndex] = staticMapUrl(stops, path);
      }
      this.mapUrls.set(urls);
      await this.imagesSettled();
    } catch {
      // без мап, але з розкладом — краще, ніж порожня сторінка
    } finally {
      this.ready.set(true);
      setTimeout(() => window.print(), 200);
    }
  }

  /** Чекає, поки всі <img> у документі або завантажаться, або впадуть. */
  private imagesSettled(): Promise<void> {
    return new Promise((resolve) => {
      setTimeout(() => {
        const images = Array.from(document.images).filter((i) => !i.complete);
        if (images.length === 0) {
          resolve();
          return;
        }
        let left = images.length;
        const done = () => {
          if (--left === 0) resolve();
        };
        images.forEach((i) => {
          i.addEventListener('load', done, { once: true });
          i.addEventListener('error', done, { once: true });
        });
        setTimeout(resolve, 8000);   // страховка: не чекаємо вічно
      });
    });
  }
}

/** "09:30" → 570. null, якщо часу немає. */
function toMinutes(time: string | null): number | null {
  if (!time) return null;
  const m = /^(\d{1,2}):(\d{2})/.exec(time);
  if (!m) return null;
  return Number(m[1]) * 60 + Number(m[2]);
}
