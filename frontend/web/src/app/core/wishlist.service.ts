import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';
import { WishlistItem, WishlistItemRequest } from './models';

/**
 * Вішліст користувача. Тримає реактивний кеш (signal), щоб усі сторінки —
 * пошук місць, картка місця, сама сторінка вішлісту — бачили однаковий стан:
 * зберіг місце в деталях → сердечко на картці пошуку одразу заповнене.
 * Додавання/видалення оновлюють кеш через tap, тож перезавантаження не треба.
 * Ходить через gateway: /api/wishlist -> trip-service.
 */
@Injectable({ providedIn: 'root' })
export class WishlistService {
  private http = inject(HttpClient);

  private _items = signal<WishlistItem[]>([]);
  readonly items = this._items.asReadonly();
  readonly savedIds = computed(() => new Set(this._items().map((i) => i.placeId)));
  readonly loading = signal(false);
  private loaded = false;

  /** Лінива загрузка: тягнемо один раз, поки не попросять force. */
  load(force = false): void {
    if (this.loaded && !force) {
      return;
    }
    this.loading.set(true);
    this.http.get<WishlistItem[]>('/api/wishlist').subscribe({
      next: (items) => {
        this._items.set(items);
        this.loaded = true;
        this.loading.set(false);
      },
      error: () => this.loading.set(false),
    });
  }

  add(req: WishlistItemRequest): Observable<WishlistItem> {
    return this.http.post<WishlistItem>('/api/wishlist', req).pipe(
      tap((item) =>
        this._items.update((list) =>
          list.some((i) => i.placeId === item.placeId) ? list : [item, ...list],
        ),
      ),
    );
  }

  remove(placeId: string): Observable<void> {
    return this.http.delete<void>(`/api/wishlist/${encodeURIComponent(placeId)}`).pipe(
      tap(() => this._items.update((list) => list.filter((i) => i.placeId !== placeId))),
    );
  }

  isSaved(placeId: string): boolean {
    return this.savedIds().has(placeId);
  }
}
