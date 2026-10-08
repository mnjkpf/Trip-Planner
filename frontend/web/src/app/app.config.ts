import { ApplicationConfig, inject, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { provideTranslateLoader, provideTranslateService, TranslateService } from '@ngx-translate/core';
import { provideTranslateHttpLoader, TranslateHttpLoader } from '@ngx-translate/http-loader';
import { firstValueFrom } from 'rxjs';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth.interceptor';
import { LANGS } from './core/lang.service';

/**
 * Визначаємо стартову мову ще до bootstrap, щоб перший рендер уже був
 * перекладений, без миготіння сирих ключів. Мова браузера НЕ враховується:
 * англійська — єдиний дефолт для всіх, доки користувач не вибере інше явно
 * (вибір живе в localStorage, а для залогінених — у профілі на бекенді).
 */
function pickInitialLang(): string {
  try {
    const saved = localStorage.getItem('tp.lang');
    if (saved && LANGS.some((l) => l.code === saved)) return saved;
  } catch { /* private mode */ }
  return 'en';
}

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes),
    provideHttpClient(withInterceptors([authInterceptor])),

    // ngx-translate v17: явно повідомляємо сервісу, що лоадер — HTTP-реалізація.
    // Без поля `loader` сервіс ставить NoOp-лоадер і жодних HTTP-запитів не робить.
    provideTranslateHttpLoader({ prefix: '/i18n/', suffix: '.json' }),
    provideTranslateService({
      fallbackLang: 'en',
      loader: provideTranslateLoader(TranslateHttpLoader),
    }),

    // Блокуємо старт застосунку, поки активна мова не завантажена.
    provideAppInitializer(() => {
      const t = inject(TranslateService);
      const lang = pickInitialLang();
      t.addLangs(LANGS.map((l) => l.code));
      return firstValueFrom(t.use(lang));
    }),
  ],
};
