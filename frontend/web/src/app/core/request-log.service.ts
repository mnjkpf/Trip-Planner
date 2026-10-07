import { Injectable, signal } from '@angular/core';

/**
 * Невеликий журнал останніх запитів до API — живить панель «Журнал подій»
 * у сайдбарі. Інтерсептор штовхає сюди кожен /api-виклик. Чисто косметично,
 * але показує «розподілену» природу застосунку в реальному часі.
 */
@Injectable({ providedIn: 'root' })
export class RequestLogService {
  private _lines = signal<string[]>([]);
  readonly lines = this._lines.asReadonly();

  push(method: string, url: string): void {
    const path = url.split('?')[0].replace(/^https?:\/\/[^/]+/, '');
    this._lines.update((l) => [`${method} ${path}`, ...l].slice(0, 6));
  }
}
