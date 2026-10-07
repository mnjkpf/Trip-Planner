import { Injectable, signal } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';

export interface LangOption { code: string; label: string; }

export const LANGS: LangOption[] = [
  { code: 'en', label: 'English' },
  { code: 'uk', label: 'Українська' },
  { code: 'pl', label: 'Polski' },
  { code: 'de', label: 'Deutsch' },
  { code: 'fr', label: 'Français' },
  { code: 'es', label: 'Español' },
  { code: 'it', label: 'Italiano' },
];

const STORAGE_KEY = 'tp.lang';
const DEFAULT_LANG = 'en';

/**
 * Перемикач мов. Початкова мова завантажується у app.config.ts
 * (provideAppInitializer) — тут керуємо лише вибором користувача.
 */
@Injectable({ providedIn: 'root' })
export class LangService {
  readonly current = signal<string>(DEFAULT_LANG);
  readonly langs = LANGS;

  constructor(private translate: TranslateService) {
    // На момент створення LangService initializer уже встановив currentLang
    this.current.set(translate.getCurrentLang() || DEFAULT_LANG);
  }

  use(code: string): void {
    const valid = LANGS.some((l) => l.code === code) ? code : DEFAULT_LANG;
    this.translate.use(valid).subscribe({
      error: (e) => console.error('[LangService] use()', valid, e),
    });
    this.current.set(valid);
    try { localStorage.setItem(STORAGE_KEY, valid); } catch { /* private mode */ }
  }
}
