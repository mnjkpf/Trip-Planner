import { Component, DestroyRef, OnInit, computed, effect, inject, input, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse, HttpEventType } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { TripPhoto, Trip } from '../core/models';
import { TripService } from '../core/trip.service';

/** Пункт маршруту для вибору «до якого місця» фото. */
export interface PhotoPlace {
  id: string;
  dayIndex: number;
  placeName: string;
}

interface UploadTask {
  name: string;
  progress: number;
  error: string | null;
}

/** Типи приймає media-service; тут лише щоб не везти даремно те, що точно відмовлять. */
const ALLOWED = ['image/jpeg', 'image/png', 'image/webp'];
const MAX_BYTES = 10 * 1024 * 1024;
const MAX_AT_ONCE = 10;

@Component({
  selector: 'app-trip-photos',
  imports: [DatePipe, TranslatePipe],
  template: `
    <div class="ph-wrap">
      @if (canEdit()) {
        <div class="ph-upload">
          <div class="ph-controls">
            <input #fileInput type="file" accept="image/jpeg,image/png,image/webp" multiple hidden
                   (change)="pick($event)" />
            <button class="btn btn-primary btn-sm" (click)="fileInput.click()" [disabled]="busy()">
              {{ busy() ? ('photos.uploading' | translate) : ('photos.add' | translate) }}
            </button>
            @if (places().length > 0) {
              <label class="ph-place">
                <span class="kicker">{{ 'photos.place' | translate }}</span>
                <select [value]="placeId() ?? ''" (change)="setPlace($event)">
                  <option value="">{{ 'photos.place_none' | translate }}</option>
                  @for (p of places(); track p.id) {
                    <option [value]="p.id">{{ 'detail.day_label' | translate }} {{ p.dayIndex }} · {{ p.placeName }}</option>
                  }
                </select>
              </label>
            }
          </div>
          <p class="muted ph-hint">{{ 'photos.hint' | translate }}</p>

          @for (u of uploads(); track $index) {
            <div class="ph-task">
              <span class="ph-task-name">{{ u.name }}</span>
              @if (u.error) {
                <span class="ph-task-err">{{ u.error | translate }}</span>
              } @else {
                <span class="ph-bar"><span class="ph-bar-fill" [style.width.%]="u.progress"></span></span>
              }
            </div>
          }
        </div>
      }

      @if (error()) { <p class="error ph-error">{{ error()! | translate }}</p> }

      @if (photos().length === 0) {
        <p class="muted ph-empty">{{ (canEdit() ? 'photos.empty' : 'photos.empty_viewer') | translate }}</p>
      } @else {
        @for (group of groups(); track group.key) {
          <section class="ph-group">
            <div class="kicker ph-group-head">
              {{ group.placeName ?? ('photos.place_none' | translate) }}
              <span class="ph-count">{{ group.photos.length }}</span>
            </div>
            <div class="ph-grid">
              @for (p of group.photos; track p.id) {
                <figure class="ph-cell" [class.pending]="p.status === 'UPLOADING'">
                  @if (p.status === 'READY') {
                    <img [src]="p.thumbUrl" [alt]="p.caption ?? p.placeName ?? ''" loading="lazy"
                         (click)="open(p)" />
                  } @else {
                    <div class="ph-pending"><span class="spinner"></span></div>
                  }
                  @if (p.mine) {
                    <button class="ph-del" (click)="remove(p)" [title]="'photos.delete' | translate">✕</button>
                  }
                  @if (p.caption) { <figcaption>{{ p.caption }}</figcaption> }
                </figure>
              }
            </div>
          </section>
        }
      }
    </div>

    @if (opened(); as p) {
      <div class="ph-box" (click)="close()">
        <button class="ph-nav prev" (click)="step(-1, $event)" [title]="'photos.prev' | translate">‹</button>
        <img [src]="p.url" [alt]="p.caption ?? ''" (click)="$event.stopPropagation()" />
        <button class="ph-nav next" (click)="step(1, $event)" [title]="'photos.next' | translate">›</button>
        <div class="ph-box-meta" (click)="$event.stopPropagation()">
          @if (p.placeName) { <span class="ph-box-place">{{ p.placeName }}</span> }
          @if (p.caption) { <span>{{ p.caption }}</span> }
          <span class="mono">{{ p.createdAt | date: 'd MMM y' }}</span>
          <button class="btn btn-secondary btn-sm" (click)="close()">{{ 'common.close' | translate }}</button>
        </div>
      </div>
    }
  `,
  host: { '(document:keydown)': 'onKey($event)' },
  styles: [`
    .ph-wrap { padding: 16px 28px 24px; display: flex; flex-direction: column; gap: 18px; }
    .ph-upload { display: flex; flex-direction: column; gap: 8px; }
    .ph-controls { display: flex; gap: 10px; align-items: center; flex-wrap: wrap; }
    .ph-place { display: flex; flex-direction: column; gap: 3px; }
    .ph-place select { min-height: 32px; font-size: 13px; max-width: 320px; }
    .ph-hint { margin: 0; font-size: 12px; }
    .ph-error { margin: 0; }
    .ph-empty { font-size: 14px; }

    .ph-task { display: flex; align-items: center; gap: 10px; font-size: 12px; }
    .ph-task-name { flex: 0 1 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .ph-task-err { color: #b91c1c; font-weight: 600; }
    .ph-bar { flex: 1 1 120px; height: 6px; background: var(--divider); overflow: hidden; }
    .ph-bar-fill { display: block; height: 100%; background: var(--accent); transition: width 0.15s linear; }

    .ph-group { display: flex; flex-direction: column; gap: 8px; }
    .ph-group-head { display: flex; align-items: center; gap: 8px; }
    .ph-count { background: var(--accent-100); color: var(--accent-800); padding: 2px 6px; font-size: 10px; }
    .ph-grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(140px, 1fr)); gap: 10px; }
    .ph-cell { position: relative; margin: 0; border: 2px solid var(--divider); background: var(--surface); }
    .ph-cell img { display: block; width: 100%; aspect-ratio: 1 / 1; object-fit: cover; cursor: zoom-in; }
    .ph-cell figcaption { padding: 6px 8px; font-size: 11px; color: var(--muted); border-top: 1px solid var(--divider-soft); }
    .ph-pending { display: grid; place-items: center; aspect-ratio: 1 / 1; background: var(--surface-2); }
    .ph-del {
      position: absolute; top: 4px; right: 4px; width: 26px; height: 26px;
      border: 1px solid var(--divider); background: rgba(255, 255, 255, 0.9);
      cursor: pointer; font-size: 12px; line-height: 1; color: var(--ink);
    }
    .ph-del:hover { border-color: #dc2626; color: #b91c1c; }

    .ph-box {
      position: fixed; inset: 0; z-index: 50; background: rgba(14, 14, 26, 0.92);
      display: flex; align-items: center; justify-content: center; padding: 24px;
    }
    .ph-box img { max-width: min(96vw, 1400px); max-height: 82vh; object-fit: contain; }
    .ph-nav {
      position: absolute; top: 50%; transform: translateY(-50%);
      width: 44px; height: 64px; border: 0; background: rgba(255, 255, 255, 0.12);
      color: #fff; font-size: 30px; line-height: 1; cursor: pointer;
    }
    .ph-nav.prev { left: 8px; }
    .ph-nav.next { right: 8px; }
    .ph-box-meta {
      position: absolute; left: 0; right: 0; bottom: 0; padding: 12px 20px;
      display: flex; gap: 12px; align-items: center; flex-wrap: wrap;
      background: rgba(14, 14, 26, 0.75); color: #fff; font-size: 13px;
    }
    .ph-box-place { font-weight: 800; }

    @media (max-width: 560px) {
      .ph-wrap { padding: 14px 16px 20px; }
      .ph-grid { grid-template-columns: repeat(auto-fill, minmax(104px, 1fr)); }
      .ph-place select { max-width: 100%; }
      .ph-nav { width: 36px; height: 52px; font-size: 24px; }
    }
  `],
})
export class TripPhotos implements OnInit {
  private trips = inject(TripService);

  trip = input.required<Trip>();
  canEdit = input(false);
  /** Пункти маршруту — щоб прив'язати фото до місця. */
  places = input<PhotoPlace[]>([]);
  /** Лічильник від батька: SSE сказав «фото змінились» — перечитуємо. */
  version = input(0);

  photos = signal<TripPhoto[]>([]);
  uploads = signal<UploadTask[]>([]);
  busy = signal(false);
  error = signal<string | null>(null);
  placeId = signal<string | null>(null);
  private openedId = signal<string | null>(null);

  private tripId = '';
  private alive = true;
  private timers: ReturnType<typeof setTimeout>[] = [];

  /** Фото групуємо за місцем: «фото Колізею» корисніше за стрічку в хронології. */
  groups = computed(() => {
    const byPlace = new Map<string, { key: string; placeName: string | null; photos: TripPhoto[] }>();
    for (const p of this.photos()) {
      const key = p.placeName ?? '';
      const group = byPlace.get(key) ?? { key: key || 'none', placeName: p.placeName, photos: [] };
      group.photos.push(p);
      byPlace.set(key, group);
    }
    // Без місця — останньою групою, решта за назвою.
    return [...byPlace.values()].sort((a, b) => {
      if (a.placeName === null) return 1;
      if (b.placeName === null) return -1;
      return a.placeName.localeCompare(b.placeName);
    });
  });

  ready = computed(() => this.photos().filter((p) => p.status === 'READY'));
  opened = computed(() => this.ready().find((p) => p.id === this.openedId()) ?? null);

  constructor() {
    // input-сигнали читаємо лише в ефекті/ngOnInit: у конструкторі required-вхід
    // ще не заповнений (NG0950).
    effect(() => {
      this.version();
      if (this.tripId) this.load();
    });
    // Відкладені перечитування не мають стріляти в уже закритий таб.
    inject(DestroyRef).onDestroy(() => {
      this.alive = false;
      this.timers.forEach(clearTimeout);
    });
  }

  ngOnInit(): void {
    this.tripId = this.trip().id;
    this.load();
  }

  private load(): void {
    if (!this.alive) return;
    this.trips.photos(this.tripId).subscribe({
      next: (list) => this.photos.set(list),
      error: (e: HttpErrorResponse) => this.error.set(e.status === 403 ? 'common.forbidden' : 'photos.load_error'),
    });
  }

  setPlace(ev: Event): void {
    const value = (ev.target as HTMLSelectElement).value;
    this.placeId.set(value || null);
  }

  pick(ev: Event): void {
    const picker = ev.target as HTMLInputElement;
    const files = Array.from(picker.files ?? []).slice(0, MAX_AT_ONCE);
    picker.value = '';         // той самий файл можна вибрати ще раз
    if (files.length > 0) this.uploadAll(files);
  }

  private later(ms: number, fn: () => void): void {
    this.timers.push(setTimeout(() => {
      if (this.alive) fn();
    }, ms));
  }

  /** Файли шлемо по черзі: так смужка прогресу чесна, а сервер не отримує залп. */
  private async uploadAll(files: File[]): Promise<void> {
    this.busy.set(true);
    this.error.set(null);
    this.uploads.set(files.map((f) => ({ name: f.name, progress: 0, error: null })));
    for (let i = 0; i < files.length; i++) {
      await this.uploadOne(files[i], i);
    }
    this.busy.set(false);
    this.load();
    // Мініатюра робиться у воркері — через кілька секунд фото стає READY.
    // SSE прийде й сам, але перечитати ще раз дешево й рятує, коли канал закритий.
    this.later(3000, () => this.load());
    this.later(8000, () => {
      this.load();
      this.uploads.set([]);
    });
  }

  private uploadOne(file: File, index: number): Promise<void> {
    if (!ALLOWED.includes(file.type)) {
      this.failTask(index, 'photos.wrong_type');
      return Promise.resolve();
    }
    if (file.size > MAX_BYTES) {
      this.failTask(index, 'photos.too_large');
      return Promise.resolve();
    }
    return new Promise((resolve) => {
      this.trips.photoTicket(this.tripId, { itemId: this.placeId(), caption: null }).subscribe({
        next: (ticket) => {
          this.trips.uploadPhoto(ticket, file).subscribe({
            next: (event) => {
              if (event.type === HttpEventType.UploadProgress && event.total) {
                this.setProgress(index, Math.round((event.loaded / event.total) * 100));
              }
              if (event.type === HttpEventType.Response) {
                this.setProgress(index, 100);
                this.load();
                resolve();
              }
            },
            error: (e: HttpErrorResponse) => {
              this.failTask(index, uploadErrorKey(e.status));
              resolve();
            },
          });
        },
        error: (e: HttpErrorResponse) => {
          this.failTask(index, uploadErrorKey(e.status));
          resolve();
        },
      });
    });
  }

  private setProgress(index: number, progress: number): void {
    this.uploads.update((list) => list.map((t, i) => (i === index ? { ...t, progress } : t)));
  }

  private failTask(index: number, key: string): void {
    this.uploads.update((list) => list.map((t, i) => (i === index ? { ...t, error: key } : t)));
  }

  remove(photo: TripPhoto): void {
    this.trips.deletePhoto(this.tripId, photo.id).subscribe({
      next: () => {
        this.photos.update((list) => list.filter((p) => p.id !== photo.id));
        if (this.openedId() === photo.id) this.close();
      },
      error: (e: HttpErrorResponse) => this.error.set(e.status === 403 ? 'photos.delete_forbidden' : 'photos.delete_error'),
    });
  }

  open(photo: TripPhoto): void {
    this.openedId.set(photo.id);
  }

  close(): void {
    this.openedId.set(null);
  }

  step(delta: number, ev: Event): void {
    ev.stopPropagation();
    const list = this.ready();
    const current = list.findIndex((p) => p.id === this.openedId());
    if (current < 0 || list.length === 0) return;
    const next = (current + delta + list.length) % list.length;
    this.openedId.set(list[next].id);
  }

  onKey(ev: KeyboardEvent): void {
    if (!this.openedId()) return;
    if (ev.key === 'Escape') this.close();
    if (ev.key === 'ArrowLeft') this.step(-1, ev);
    if (ev.key === 'ArrowRight') this.step(1, ev);
  }
}

/** Коди медіа-сервісу у ключі перекладу. */
function uploadErrorKey(status: number): string {
  if (status === 403) return 'photos.forbidden';
  if (status === 413) return 'photos.too_large';
  if (status === 415) return 'photos.wrong_type';
  if (status === 503) return 'photos.unavailable';
  return 'photos.upload_error';
}
