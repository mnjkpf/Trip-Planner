import { Injectable, computed, signal } from '@angular/core';
import { CreateTripRequest, PlanPreview, TripDraft } from './models';

const KEY = 'tp.draft';

/**
 * Чернетка подорожі, яку гість спланував без акаунта.
 *
 * Лежить у localStorage, а не в БД: сенс усієї фічі в тому, що до реєстрації
 * ми нічого про людину не зберігаємо. Платою за це є межі браузера — чернетка
 * не переживе ні чистку даних сайту, ні перехід на інший пристрій, і про це
 * сторінка прев'ю чесно попереджає.
 *
 * Усі звернення до сховища обгорнуті try/catch: у приватному режимі Safari і
 * при заблокованих site data localStorage кидає, а втрата чернетки не привід
 * валити застосунок.
 */
@Injectable({ providedIn: 'root' })
export class DraftService {
  private _draft = signal<TripDraft | null>(this.read());

  readonly draft = this._draft.asReadonly();
  readonly hasDraft = computed(() => this._draft() !== null);

  save(request: CreateTripRequest, preview: PlanPreview): void {
    const draft: TripDraft = { request, preview, createdAt: new Date().toISOString() };
    this._draft.set(draft);
    try {
      localStorage.setItem(KEY, JSON.stringify(draft));
    } catch {
      // Сховище недоступне — чернетка житиме лише до перезавантаження.
    }
  }

  clear(): void {
    this._draft.set(null);
    try {
      localStorage.removeItem(KEY);
    } catch {
      /* ignore */
    }
  }

  private read(): TripDraft | null {
    try {
      const raw = localStorage.getItem(KEY);
      if (!raw) return null;
      const parsed = JSON.parse(raw) as TripDraft;
      // Мінімальна перевірка форми: сховище переживає деплої, а структура
      // могла змінитися. Биту чернетку краще мовчки викинути, ніж упасти.
      return parsed?.request && parsed?.preview?.days ? parsed : null;
    } catch {
      return null;
    }
  }
}
