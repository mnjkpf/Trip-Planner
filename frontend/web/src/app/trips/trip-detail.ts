import { Component, ElementRef, OnDestroy, computed, effect, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { clearOverlays, createMap, fit, loadMaps, numberedMarker, routeByRoads, routePolyline } from '../core/map';
import { Observable, switchMap, take, takeWhile, timer } from 'rxjs';
import { TripBudget } from './trip-budget';
import { TripFlights } from './trip-flights';
import { TripMembers } from './trip-members';
import { TripHotels } from './trip-hotels';
import { TripService } from '../core/trip.service';
import { AuthService } from '../core/auth.service';
import { WishlistService } from '../core/wishlist.service';
import {
  ExpenseRequest,
  Itinerary,
  ItineraryDay,
  ItineraryItem,
  ShareLink,
  TravelContext,
  Trip,
  TripRole,
  WishlistItem,
} from '../core/models';
import { preferenceChipKeys } from './plan-preferences-editor';

@Component({
  selector: 'app-trip-detail',
  imports: [DatePipe, RouterLink, TripHotels, TripFlights, TripBudget, TripMembers, TranslatePipe],
  template: `
    @if (trip(); as t) {
      <div class="page">
        <div class="d-head">
          <div class="grow">
            <a class="back" routerLink="/trips">{{ 'detail.back_to_trips' | translate }}</a>
            <h1 class="d-title">{{ t.title }}</h1>
            <div class="d-meta">
              <span>{{ t.destinationName }}</span>
              <span class="sep">·</span>
              <span>{{ t.startDate | date: 'd MMM' }} – {{ t.endDate | date: 'd MMM y' }}</span>
              <span class="tag" [class]="'st-' + t.status">{{ statusLabel(t.status) }}</span>
            </div>
            @if (prefChips().length > 0) {
              <div class="d-prefs" [title]="'prefs.title' | translate">
                @for (c of prefChips(); track $index) {
                  <span class="d-pref">{{ c | translate }}</span>
                }
              </div>
            }
          </div>
          <div class="d-actions">
            @if (t.status === 'PLANNED') {
              <a class="btn btn-secondary btn-sm" [routerLink]="['/trips', t.id, 'print']" target="_blank">
                {{ 'export.pdf' | translate }}
              </a>
              @if (canEdit()) {
                <button class="btn btn-secondary btn-sm" (click)="toggleShare()">{{ 'share.btn' | translate }}</button>
              }
            }
            @if (t.status !== 'PLANNING' && canEdit()) {
              <a class="btn btn-secondary btn-sm" [routerLink]="['/trips', t.id, 'edit']">{{ 'detail.edit_btn' | translate }}</a>
            }
            @if (isOwner()) {
              <button class="btn btn-secondary btn-sm" (click)="membersOpen.set(!membersOpen())">
                {{ 'members.btn' | translate }}
              </button>
            } @else {
              <span class="role-chip">{{ 'members.role_' + role().toLowerCase() | translate }}</span>
            }
            @if (isOwner() && !confirmingDelete()) {
              <button class="btn btn-danger btn-sm" (click)="confirmingDelete.set(true)">{{ 'detail.delete_btn' | translate }}</button>
            } @else {
              <span class="confirm">
                {{ 'detail.confirm_delete' | translate }}
                <button class="btn btn-danger btn-sm" (click)="remove()" [disabled]="deleting()">{{ deleting() ? '…' : ('common.yes' | translate) }}</button>
                <button class="btn btn-secondary btn-sm" (click)="confirmingDelete.set(false)" [disabled]="deleting()">{{ 'common.no' | translate }}</button>
              </span>
            }
          </div>
        </div>

        @if (membersOpen() || !isOwner()) {
          <div class="section members-sec">
            <app-trip-members [trip]="t" (left)="onLeft()" />
          </div>
        }

        @if (sharePanel()) {
          <div class="section share-sec">
            <div class="kicker">{{ 'share.title' | translate }}</div>
            @if (shareLink(); as link) {
              <p class="muted share-desc">{{ 'share.desc' | translate }}</p>
              <div class="share-row">
                <input class="share-url mono" readonly [value]="shareUrl(link)" (focus)="selectAll($event)" />
                <button class="btn btn-secondary btn-sm" (click)="copyShare(link)">
                  {{ copied() ? ('share.copied' | translate) : ('share.copy' | translate) }}
                </button>
                <button class="btn btn-danger btn-sm" (click)="revokeShare()" [disabled]="shareBusy()">
                  {{ 'share.revoke' | translate }}
                </button>
              </div>
            } @else {
              <p class="muted share-desc">{{ 'share.empty' | translate }}</p>
              <button class="btn btn-primary btn-sm" (click)="createShare()" [disabled]="shareBusy()">
                {{ shareBusy() ? '…' : ('share.create' | translate) }}
              </button>
            }
          </div>
        }

        @if (t.status !== 'PLANNED') {
          <div class="section plan-panel">
            <h3>{{ 'detail.not_planned_title' | translate }}</h3>
            <p class="muted plan-body">
              {{ 'detail.not_planned_desc' | translate }}
              
            </p>
            <button class="btn btn-primary" (click)="plan()" [disabled]="planning() || !canEdit()">
              {{ planning() ? ('detail.planning' | translate) : ('detail.plan_btn' | translate) }}
            </button>
            @if (planning()) {
              <div class="plan-run"><span class="spinner"></span><span class="mono">{{ 'detail.plan_pipeline' | translate }}</span></div>
            }
          </div>
        }

        @if (error()) { <div class="section err-sec"><p class="error">{{ error() }}</p></div> }

        @if (ctx(); as c) {
          <div class="ctx-strip">
            <div class="ctx-season">
              <div class="kicker">{{ 'detail.season' | translate }}</div>
              <div class="ctx-name">{{ seasonLabel(c.season) }}</div>
              @if (climateHint(c); as hint) { <div class="ctx-hint">{{ hint }}</div> }
            </div>
            <div class="ctx-days">
              @for (d of c.days; track d.date) {
                <div class="ctx-day" [class.warn]="(d.precipitationMm ?? 0) > 0">
                  <div class="kicker">{{ d.date | date: 'd MMM' }}</div>
                  @if (d.tempMaxC !== null) {
                    <div class="ctx-temp">{{ d.tempMinC }}° / {{ d.tempMaxC }}°</div>
                  } @else { <div class="ctx-temp muted">{{ 'detail.only_season' | translate }}</div> }
                  @if ((d.precipitationMm ?? 0) > 0) { <div class="ctx-rain">{{ 'detail.rain' | translate }} {{ d.precipitationMm }} mm</div> }
                </div>
              }
            </div>
          </div>
        }

        @if (trip()) {
          <div class="section-soft tab-bar">
            <button class="tab" [class.on]="tab() === 'route'" (click)="tab.set('route')">
              {{ 'tabs.route' | translate }}
            </button>
            <button class="tab" [class.on]="tab() === 'hotels'" (click)="tab.set('hotels')">
              {{ 'tabs.hotels' | translate }}
            </button>
            <button class="tab" [class.on]="tab() === 'flights'" (click)="tab.set('flights')">
              {{ 'tabs.flights' | translate }}
            </button>
            <button class="tab" [class.on]="tab() === 'budget'" (click)="tab.set('budget')">
              {{ 'tabs.budget' | translate }}
            </button>
          </div>
        }

        @if (tab() === 'hotels' && trip(); as t) {
          <app-trip-hotels [trip]="t" (addToBudget)="saveExpense($event)" />
        }

        @if (tab() === 'flights' && trip(); as t) {
          <app-trip-flights [trip]="t" (addToBudget)="saveExpense($event)" />
        }

        @if (tab() === 'budget' && trip(); as t) {
          <app-trip-budget [trip]="t" />
        }

        @if (tab() === 'route' && itinerary(); as it) {
          <div class="section-soft day-bar">
            <div class="day-tabs">
              @for (d of it.days; track d.dayIndex) {
                <button class="day-tab" [class.on]="openDay() === d.dayIndex"
                        (click)="openDay.set(d.dayIndex)"
                        (dragover)="edit() && onDragOver($event)"
                        (drop)="edit() && onDropTab(d.dayIndex, $event)">
                  <span class="dt-n">{{ d.dayIndex }}</span>
                  <span class="dt-date">{{ d.date | date: 'd MMM' }}</span>
                </button>
              }
            </div>
            @if (canEdit()) {
              <button class="btn btn-secondary btn-sm edit-toggle" (click)="toggleEdit()">
                {{ edit() ? ('edit.toggle_done' | translate) : ('edit.toggle_edit' | translate) }}
              </button>
            }
          </div>

          <div class="trip-split">
            <div class="trip-map-wrap"><div #mapEl class="trip-map"></div></div>
            <div class="trip-day">
              @if (activeDay(); as day) {
                <div class="day-head">
                  <span class="dh-title">{{ 'detail.day_label' | translate }} {{ day.dayIndex }} · {{ day.date | date: 'EEEE, d MMM' }}</span>
                  @if (day.items.length) { <span class="dh-stats mono">{{ 'day.stats' | translate: {km: day.distanceKm, min: day.walkMinutes} }}</span> }
                </div>

                @if (day.items.length === 0) {
                  <div class="muted no-items">{{ 'edit.no_items' | translate }}@if (edit()) { {{ 'edit.add_hint' | translate }} }</div>
                } @else {
                  @for (item of day.items; track item.id) {
                    <div class="trip-item" [class.locked]="item.locked"
                         (dragover)="edit() && onDragOver($event)"
                         (drop)="edit() && onDropItem(item, $event)">
                      @if (edit()) {
                        <span class="drag-handle" draggable="true" (dragstart)="onDragStart(item, $event)" [title]="'edit.drag' | translate">⠿</span>
                      }
                      <span class="ti-ord">{{ item.order }}</span>
                      <div class="ti-body">
                        <div class="ti-top">
                          <span class="ti-name">{{ item.placeName }}</span>
                          @if (item.placeCategory) { <span class="tag tag-accent-2">{{ catLabel(item.placeCategory) | translate }}</span> }
                          @if (item.locked) { <span class="lock-badge" [title]="'edit.pinned' | translate">🔒</span> }
                        </div>
                        @if (item.travelMinutesFromPrev) { <div class="ti-travel mono">↳ {{ 'edit.walk_min' | translate: {n: item.travelMinutesFromPrev} }}</div> }
                        @if (edit()) {
                          <div class="ti-edit">
                            <input type="time" class="ti-in ti-time-in" [value]="item.time ?? ''" (change)="setTime(item, $event)" />
                            <input class="ti-in ti-note-in" [placeholder]="'edit.note_hint' | translate" [value]="item.note ?? ''" (change)="setNote(item, $event)" />
                            <button class="icon-btn" [class.on]="item.locked" (click)="toggleLock(item)" [title]="'edit.pin' | translate">🔒</button>
                            <button class="icon-btn" (click)="moveWithin(item, -1)" [disabled]="item.order === 1" [title]="'edit.move_up' | translate">↑</button>
                            <button class="icon-btn" (click)="moveWithin(item, 1)" [disabled]="item.order === day.items.length" [title]="'edit.move_down' | translate">↓</button>
                            <button class="icon-btn danger" (click)="removeItem(item)" [title]="'edit.remove' | translate">✕</button>
                          </div>
                        }
                      </div>
                      @if (item.time) { <span class="ti-time mono">{{ item.time }}</span> }
                    </div>
                  }
                }

                @if (edit()) {
                  <div class="add-panel">
                    <div class="add-head">
                      <span class="kicker">{{ 'edit.add_to_day' | translate: {n: openDay()} }}</span>
                      @if (day.items.length > 1) {
                        <button class="btn btn-ghost btn-sm" (click)="optimize()">{{ 'edit.optimize_btn' | translate }}</button>
                      }
                    </div>
                    @if (addCandidates().length === 0) {
                      <div class="muted add-empty">{{ 'edit.all_in_route' | translate }} <a routerLink="/places">{{ 'edit.find_more' | translate }}</a></div>
                    } @else {
                      @for (w of addCandidates(); track w.placeId) {
                        <div class="add-row">
                          <span class="aw-name">{{ w.placeName }}</span>
                          <button class="btn btn-secondary btn-sm" (click)="addFromWishlist(w)">{{ 'edit.add_btn' | translate: {n: openDay()} }}</button>
                        </div>
                      }
                    }
                  </div>
                }
              }
            </div>
          </div>
        }
      </div>
    } @else {
      <div class="section"><p class="muted">{{ 'common.loading' | translate }}</p></div>
    }
  `,
  styles: [`
    .d-head { display: flex; flex-wrap: wrap; gap: 16px; align-items: flex-end; padding: 20px 28px 16px; border-bottom: 2px solid var(--divider); }
    .d-title { margin: 8px 0 6px; }
    .d-meta { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; font-size: 13px; color: var(--muted); }
    .role-chip {
      align-self: center; background: var(--accent-100); color: var(--accent-800);
      font-size: 10px; text-transform: uppercase; letter-spacing: 0.06em; padding: 4px 8px;
    }
    .members-sec { display: block; }
    .share-sec { display: flex; flex-direction: column; gap: 10px; align-items: flex-start; }
    .share-desc { margin: 0; font-size: 12px; }
    .share-row { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; width: 100%; }
    .share-url {
      flex: 1 1 320px; min-width: 220px; font-size: 12px;
      border: 2px solid var(--divider); padding: 7px 10px; background: var(--surface); color: var(--ink);
    }
    .d-prefs { display: flex; flex-wrap: wrap; gap: 6px; margin-top: 10px; }
    .d-pref {
      background: var(--accent-100); color: var(--accent-800);
      font: 700 11px/1 var(--font); padding: 4px 8px;
    }
    .d-meta .sep { opacity: 0.4; }
    .d-actions { display: flex; gap: 8px; flex-wrap: wrap; }
    .confirm { display: inline-flex; align-items: center; gap: 8px; font-size: 13px; color: var(--muted); }

    .plan-panel { background: var(--surface-2); display: flex; flex-direction: column; align-items: flex-start; gap: 10px; }
    .plan-panel h3 { margin: 0; }
    .plan-body { max-width: 56ch; margin: 0; font-size: 13px; }
    .plan-run { display: flex; align-items: center; gap: 10px; font-size: 12px; color: var(--muted); }
    .err-sec { border-bottom: 0; padding-top: 14px; padding-bottom: 0; }

    .ctx-strip { display: flex; flex-wrap: wrap; align-items: stretch; border-bottom: 2px solid var(--divider); }
    .ctx-season { padding: 14px 28px; border-right: 1px solid var(--divider-soft); min-width: 180px; }
    .ctx-name { font: 800 17px/1.2 var(--font); margin-top: 4px; }
    .ctx-hint { font-size: 11px; color: var(--muted); margin-top: 3px; max-width: 24ch; line-height: 1.45; }
    .ctx-days { display: flex; flex-wrap: wrap; flex: 1; }
    .ctx-day { padding: 14px 20px; border-right: 1px solid var(--divider-soft); min-width: 110px; }
    .ctx-day.warn { background: var(--lime); }
    .ctx-temp { font: 800 17px/1.2 var(--font); margin-top: 4px; }
    .ctx-rain { font-size: 11px; color: var(--accent-700); margin-top: 3px; font-weight: 600; }

    .day-bar { padding: 0 28px; display: flex; align-items: stretch; gap: 14px; flex-wrap: wrap; }
    .day-tabs { display: flex; flex-wrap: wrap; margin-right: auto; }
    .day-tab { display: flex; flex-direction: column; gap: 2px; align-items: flex-start; background: transparent; border: 0; border-right: 1px solid var(--divider-soft); padding: 12px 18px; cursor: pointer; color: var(--ink); }
    .day-tab:hover:not(.on) { background: rgba(14, 14, 26, 0.05); }
    .day-tab.on { background: var(--accent); color: var(--bg); }
    .dt-n { font: 800 15px/1 var(--font); }
    .dt-date { font-size: 10px; letter-spacing: 0.06em; text-transform: uppercase; opacity: 0.7; }
    .edit-toggle { align-self: center; }

    .trip-split { display: flex; flex-wrap: wrap; flex: 1; min-height: 0; }
    .trip-map-wrap { flex: 1 1 300px; min-width: 240px; position: relative; min-height: 400px; }
    .trip-map { position: absolute; inset: 0; background: var(--surface); }
    .trip-day { flex: 1 1 360px; min-width: 300px; border-left: 2px solid var(--divider); display: flex; flex-direction: column; }
    .day-head { display: flex; flex-wrap: wrap; gap: 8px; align-items: baseline; justify-content: space-between; padding: 14px 20px; border-bottom: 1px solid var(--divider-soft); }
    .dh-title { font: 800 15px/1.2 var(--font); text-transform: capitalize; }
    .dh-stats { font-size: 11px; color: var(--accent-700); }
    .no-items { padding: 16px 20px; font-size: 14px; }

    .trip-item { display: flex; gap: 12px; align-items: flex-start; padding: 13px 20px; border-bottom: 1px solid var(--divider-soft); }
    .trip-item.locked { background: color-mix(in srgb, var(--lime) 22%, transparent); }
    .drag-handle { cursor: grab; color: var(--muted-2); font-size: 15px; line-height: 26px; user-select: none; }
    .ti-ord { width: 26px; height: 26px; flex: none; background: var(--accent); color: var(--bg); display: inline-flex; align-items: center; justify-content: center; font: 800 12px/1 var(--font); }
    .ti-body { flex: 1; min-width: 0; }
    .ti-top { display: flex; gap: 8px; align-items: baseline; flex-wrap: wrap; }
    .ti-name { font: 800 15px/1.25 var(--font); }
    .lock-badge { font-size: 11px; }
    .ti-travel { font-size: 11px; color: var(--muted); margin-top: 3px; }
    .ti-time { font-size: 12px; color: var(--ink); flex: none; }

    .ti-edit { display: flex; gap: 6px; align-items: center; flex-wrap: wrap; margin-top: 8px; }
    .ti-in { min-height: 30px; padding: 4px 8px; font-size: 13px; }
    .ti-time-in { width: 108px; flex: none; }
    .ti-note-in { flex: 1 1 120px; min-width: 100px; }
    .icon-btn { width: 30px; height: 30px; flex: none; border: 1px solid var(--divider); background: #fff; cursor: pointer; font-size: 13px; line-height: 1; color: var(--ink); }
    .icon-btn:hover:not(:disabled) { border-color: var(--accent); }
    .icon-btn:disabled { opacity: 0.4; cursor: default; }
    .icon-btn.on { background: var(--lime); border-color: var(--divider); }
    .icon-btn.danger:hover { border-color: #dc2626; color: #b91c1c; }

    .add-panel { border-top: 2px solid var(--divider); padding: 14px 20px; display: flex; flex-direction: column; gap: 8px; background: var(--surface-2); }
    .add-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
    .add-empty { font-size: 13px; }
    .add-row { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
    .aw-name { font-weight: 600; font-size: 14px; }

    .tab-bar { display: flex; gap: 0; padding: 0 28px; border-bottom: 2px solid var(--divider); }
    .tab { background: transparent; border: 0; padding: 14px 24px;
      font: 800 13px/1 var(--font); letter-spacing: 0.04em; text-transform: uppercase;
      color: var(--muted); cursor: pointer; border-bottom: 3px solid transparent; }
    .tab:hover { color: var(--ink); }
    .tab.on { color: var(--accent); border-bottom-color: var(--accent); }
  `],
})
export class TripDetail implements OnDestroy {
  private route = inject(ActivatedRoute);
  private router = inject(Router);
  private trips = inject(TripService);
  private i18n = inject(TranslateService);
  private auth = inject(AuthService);
  protected wishlist = inject(WishlistService);

  id = this.route.snapshot.paramMap.get('id')!;
  trip = signal<Trip | null>(null);
  /** Вибрані фільтри планування — чипами «тільки читати» у шапці. */
  prefChips = computed(() => preferenceChipKeys(this.trip()?.preferences));
  itinerary = signal<Itinerary | null>(null);
  tab = signal<'route' | 'hotels' | 'flights' | 'budget'>('route');
  ctx = signal<TravelContext | null>(null);
  planning = signal(false);
  sharePanel = signal(false);
  membersOpen = signal(false);
  shareLink = signal<ShareLink | null>(null);
  shareBusy = signal(false);
  copied = signal(false);
  error = signal<string | null>(null);
  confirmingDelete = signal(false);
  deleting = signal(false);
  openDay = signal(1);
  edit = signal(false);

  private mapEl = viewChild<ElementRef<HTMLDivElement>>('mapEl');
  private map: google.maps.Map | null = null;
  private overlays: (google.maps.marker.AdvancedMarkerElement | google.maps.Polyline)[] = [];
  private routeToken = 0;
  private mapsReady = false;
  private dragId: string | null = null;

  activeDay = computed<ItineraryDay | null>(() => {
    const it = this.itinerary();
    if (!it || it.days.length === 0) return null;
    return it.days.find((d) => d.dayIndex === this.openDay()) ?? it.days[0];
  });

  private itineraryPlaceIds = computed(() => {
    const set = new Set<string>();
    this.itinerary()?.days.forEach((d) => d.items.forEach((i) => set.add(i.placeId)));
    return set;
  });
  addCandidates = computed<WishlistItem[]>(() =>
    this.wishlist.items().filter((w) => !this.itineraryPlaceIds().has(w.placeId)),
  );

  private readonly catLabels: Record<string, string> = {
    HOTEL: 'category.HOTEL', RESTAURANT: 'category.RESTAURANT', CAFE: 'category.CAFE', BAR: 'category.BAR', MUSEUM: 'category.MUSEUM',
    ATTRACTION: 'category.ATTRACTION', PARK: 'category.PARK', BEACH: 'category.BEACH', SHOP: 'category.SHOP', OTHER: 'category.OTHER',
  };

  constructor() {
    this.load();
    effect(() => {
      const it = this.itinerary();
      const el = this.mapEl()?.nativeElement;
      this.openDay();
      if (it && el) {
        this.ensureMap(el);
        this.drawDay();
      }
    });
  }

  catLabel(c: string): string {
    const m: Record<string, string> = { HOTEL:'category.HOTEL',RESTAURANT:'category.RESTAURANT',CAFE:'category.CAFE',BAR:'category.BAR',MUSEUM:'category.MUSEUM',ATTRACTION:'category.ATTRACTION',PARK:'category.PARK',BEACH:'category.BEACH',SHOP:'category.SHOP',OTHER:'category.OTHER' };
    return m[c] ?? c;
  }

  // ── editing ──
  toggleEdit(): void {
    this.edit.update((v) => !v);
    if (this.edit()) this.wishlist.load();
  }

  private apply(obs: Observable<Itinerary>): void {
    obs.subscribe({
      next: (it) => this.itinerary.set(it),
      error: () => this.error.set('edit.update_error'),
    });
  }

  removeItem(item: ItineraryItem): void {
    this.apply(this.trips.itinRemove(this.id, item.id));
  }
  toggleLock(item: ItineraryItem): void {
    this.apply(this.trips.itinPatch(this.id, item.id, { locked: !item.locked }));
  }
  setTime(item: ItineraryItem, ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    this.apply(this.trips.itinPatch(this.id, item.id, { plannedStart: v || '' }));
  }
  setNote(item: ItineraryItem, ev: Event): void {
    const v = (ev.target as HTMLInputElement).value;
    this.apply(this.trips.itinPatch(this.id, item.id, { note: v }));
  }
  moveWithin(item: ItineraryItem, delta: number): void {
    this.apply(this.trips.itinMove(this.id, item.id, this.openDay(), item.order + delta));
  }
  optimize(): void {
    this.apply(this.trips.itinOptimize(this.id, this.openDay()));
  }
  addFromWishlist(w: WishlistItem): void {
    this.apply(
      this.trips.itinAdd(this.id, this.openDay(), {
        placeId: w.placeId,
        placeName: w.placeName,
        category: null,
        lat: w.placeLat ?? 0,
        lon: w.placeLon ?? 0,
      }),
    );
  }

  // drag & drop
  onDragStart(item: ItineraryItem, ev: DragEvent): void {
    this.dragId = item.id;
    ev.dataTransfer?.setData('text/plain', item.id);
    if (ev.dataTransfer) ev.dataTransfer.effectAllowed = 'move';
  }
  onDragOver(ev: DragEvent): void {
    ev.preventDefault();
  }
  onDropItem(target: ItineraryItem, ev: DragEvent): void {
    ev.preventDefault();
    const id = this.dragId;
    this.dragId = null;
    if (id && id !== target.id) {
      this.apply(this.trips.itinMove(this.id, id, this.openDay(), target.order));
    }
  }
  onDropTab(dayIndex: number, ev: DragEvent): void {
    ev.preventDefault();
    const id = this.dragId;
    this.dragId = null;
    if (id) {
      this.apply(this.trips.itinMove(this.id, id, dayIndex, 999));
    }
  }

  private async ensureMap(el: HTMLDivElement): Promise<void> {
    // DOM-елемент може перестворитись при зміні табу — тоді стара мапа мертва.
    if (this.map && this.map.getDiv() !== el) {
      clearOverlays(this.overlays); this.overlays = []; this.map = null;
    }
    if (this.map) return;
    if (!this.mapsReady && !(await this.initMaps())) return;
    this.map = createMap(el, { lat: 41.9028, lng: 12.4964 }, 13);
    this.drawDay();
  }

  private async initMaps(): Promise<boolean> {
    try { await loadMaps(); this.mapsReady = true; return true; }
    catch { return false; }
  }

  private drawDay(): void {
    if (!this.map) return;
    clearOverlays(this.overlays);
    this.overlays = [];
    const day = this.activeDay();
    if (!day || day.items.length === 0) return;
    const info = new google.maps.InfoWindow();
    const pts: google.maps.LatLngLiteral[] = day.items.map((i) => ({ lat: i.lat, lng: i.lon }));
    day.items.forEach((i, idx) => {
      const m = numberedMarker(this.map!, pts[idx], i.order, i.placeName);
      m.addListener('click', () => {
        info.setContent('<b>' + this.escape(i.placeName) + '</b>' + (i.time ? '<br>' + this.escape(i.time) : ''));
        info.open({ map: this.map!, anchor: m });
      });
      this.overlays.push(m);
    });
    fit(this.map, pts, 15);
    if (pts.length > 1) this.drawRoute(pts);
  }

  /** Малює маршрут по дорогах; на провал Directions API — пунктирна пряма. */
  private async drawRoute(pts: google.maps.LatLngLiteral[]): Promise<void> {
    const token = ++this.routeToken;
    const path = await routeByRoads(pts);
    if (!this.map || token !== this.routeToken) return; // перемкнули день / закрили
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

  private escape(s: string): string {
    const m: Record<string, string> = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;' };
    return s.replace(/[&<>"]/g, (ch) => m[ch]);
  }

  ngOnDestroy(): void {
    clearOverlays(this.overlays);
    this.overlays = [];
  }

  private load() {
    this.trips.get(this.id).subscribe({
      next: (t) => {
        this.trip.set(t);
        if (t.status === 'PLANNED') {
          this.loadItinerary();
          this.loadContext(t);
        } else if (t.status === 'PLANNING') {
          // планування вже запущене (напр. автоматично після створення) — підхоплюємо SSE
          this.planning.set(true);
          this.streamEvents(this.id);
        }
      },
      error: () => this.error.set(this.i18n.instant('detail.load_error')),
    });
  }

  remove() {
    this.deleting.set(true);
    this.error.set(null);
    this.trips.remove(this.id).subscribe({
      next: () => this.router.navigate(['/trips']),
      error: () => {
        this.deleting.set(false);
        this.confirmingDelete.set(false);
        this.error.set(this.i18n.instant('detail.delete_error'));
      },
    });
  }

  plan() {
    this.planning.set(true);
    this.error.set(null);
    this.trips.plan(this.id).subscribe({
      next: () => this.streamEvents(this.id),
      error: () => {
        this.planning.set(false);
        this.error.set(this.i18n.instant('detail.plan_start_error'));
      },
    });
  }

  private poll() {
    timer(0, 2000)
      .pipe(
        switchMap(() => this.trips.get(this.id)),
        takeWhile((t) => t.status !== 'PLANNED', true),
        take(30),
      )
      .subscribe({
        next: (t) => {
          this.trip.set(t);
          if (t.status === 'PLANNED') {
            this.planning.set(false);
            this.loadItinerary();
            this.loadContext(t);
          }
        },
        error: () => {
          this.planning.set(false);
          this.error.set(this.i18n.instant('detail.plan_error'));
        },
        complete: () => this.planning.set(false),
      });
  }

  private async streamEvents(id: string): Promise<void> {
    const token = this.auth.token;
    try {
      const res = await fetch(`/api/trips/${id}/plan-events`, {
        headers: token ? { Authorization: `Bearer ${token}` } : {},
      });
      if (!res.ok || !res.body) {
        this.poll();
        return;
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder();
      let buf = '';
      let planned = false;
      for (;;) {
        const { done, value } = await reader.read();
        if (value) {
          buf += decoder.decode(value, { stream: true });
        }
        if (buf.includes('PLANNED')) {
          planned = true;
          break;
        }
        if (done) {
          break;
        }
      }
      reader.cancel().catch(() => undefined);
      if (planned) {
        this.onPlanned(id);
      } else {
        this.poll();
      }
    } catch {
      this.poll();
    }
  }

  private onPlanned(id: string): void {
    this.trips.get(id).subscribe({
      next: (t) => {
        this.trip.set(t);
        this.planning.set(false);
        this.loadItinerary();
        this.loadContext(t);
      },
      error: () => this.planning.set(false),
    });
  }

  /**
   * «+ до бюджету» з карток рейсу чи готелю. Витрату пише цей компонент, а не
   * дочірній: таб бюджету створюється заново при кожному відкритті й тягне
   * свіже зведення сам, тож достатньо просто переключитись на нього.
   */
  saveExpense(e: ExpenseRequest): void {
    this.trips.addExpense(this.id, e).subscribe({
      next: () => this.tab.set('budget'),
      error: () => this.error.set(this.i18n.instant('budget.error')),
    });
  }

  /**
   * Роль викликача. Якщо бекенд поля ще не віддає (стара версія trip-service),
   * вважаємо OWNER: до появи учасників доступ до подорожі мав лише її автор,
   * тож це і є правильна поведінка для перехідного стану — і UI не падає на
   * undefined замість того, щоб просто не показати кілька кнопок.
   */
  role(): TripRole {
    return this.trip()?.role ?? 'OWNER';
  }

  isOwner(): boolean {
    return this.role() === 'OWNER';
  }

  /** Глядач не редагує нічого: ні подорож, ні маршрут, ні бюджет. */
  canEdit(): boolean {
    return this.role() !== 'VIEWER';
  }

  /** Сам вийшов зі спільної подорожі — доступу більше немає, вертаємось до списку. */
  onLeft(): void {
    this.router.navigate(['/trips']);
  }

  // ── публічне посилання ──
  /** Відкриваючи панель уперше, підтягуємо вже створене посилання (404 = немає). */
  toggleShare(): void {
    const open = !this.sharePanel();
    this.sharePanel.set(open);
    if (!open || this.shareLink()) return;
    this.trips.shareCurrent(this.id).subscribe({
      next: (l) => this.shareLink.set(l),
      error: () => this.shareLink.set(null),
    });
  }

  createShare(): void {
    this.shareBusy.set(true);
    this.trips.share(this.id).subscribe({
      next: (l) => {
        this.shareLink.set(l);
        this.shareBusy.set(false);
      },
      error: () => {
        this.error.set(this.i18n.instant('share.error'));
        this.shareBusy.set(false);
      },
    });
  }

  revokeShare(): void {
    this.shareBusy.set(true);
    this.trips.shareRevoke(this.id).subscribe({
      next: () => {
        this.shareLink.set(null);
        this.copied.set(false);
        this.shareBusy.set(false);
      },
      error: () => {
        this.error.set(this.i18n.instant('share.error'));
        this.shareBusy.set(false);
      },
    });
  }

  /** Бекенд віддає лише шлях — домен знає тільки браузер. */
  shareUrl(link: ShareLink): string {
    return window.location.origin + link.path;
  }

  async copyShare(link: ShareLink): Promise<void> {
    const url = this.shareUrl(link);
    try {
      // clipboard API є лише в secure context (https або localhost)
      await navigator.clipboard.writeText(url);
      this.copied.set(true);
      setTimeout(() => this.copied.set(false), 2000);
    } catch {
      // не вийшло — просто виділяємо текст, щоб користувач скопіював вручну
      const input = document.querySelector<HTMLInputElement>('.share-url');
      input?.select();
    }
  }

  selectAll(ev: Event): void {
    (ev.target as HTMLInputElement).select();
  }

  private loadItinerary() {
    this.trips.itinerary(this.id).subscribe({ next: (it) => this.itinerary.set(it) });
  }

  private loadContext(t: Trip) {
    this.trips
      .context(t.destinationLat, t.destinationLon, t.startDate, t.endDate)
      .subscribe({ next: (c) => this.ctx.set(c) });
  }

  statusLabel(s: string): string {
    const key = 'trips.status_' + s.toLowerCase();
    const label = this.i18n.instant(key);
    return label === key ? s : label;
  }

  /**
   * Підказку віддає context-service кодом (climateHintCode) — перекладаємо його.
   * Якщо код новий і фронт його ще не знає, показуємо англійський фолбек з API.
   */
  climateHint(c: TravelContext): string {
    if (c.climateHintCode) {
      const key = 'detail.climate_' + c.climateHintCode.toLowerCase();
      const text = this.i18n.instant(key);
      if (text !== key) return text;
    }
    return c.climateHint ?? '';
  }

  seasonLabel(s: string): string {
    const key = 'detail.season_' + s.toLowerCase();
    const label = this.i18n.instant(key);
    return label === key ? s : label;
  }
}
