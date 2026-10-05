import { Injectable, signal } from '@angular/core';
import { environment } from '../../environments/environment';

/**
 * Обгортка над Google Identity Services (GIS).
 *
 * Чому GIS, а не classic OAuth redirect: не треба налаштовувати redirect_uri
 * (що болить із Docker/localhost), немає серверного обміну кодом — Google
 * одразу віддає ID-token у браузер, а бекенд лише перевіряє його підпис.
 *
 * Скрипт вантажиться ліниво: тільки коли відкрито login/register.
 */
declare global {
  interface Window {
    google?: {
      accounts?: {
        id?: {
          initialize(config: {
            client_id: string;
            callback: (res: { credential: string }) => void;
            auto_select?: boolean;
            cancel_on_tap_outside?: boolean;
          }): void;
          renderButton(parent: HTMLElement, options: Record<string, unknown>): void;
          prompt(): void;
        };
      };
    };
  }
}

let loader: Promise<void> | null = null;

@Injectable({ providedIn: 'root' })
export class GoogleAuthService {
  /** false, якщо client-id не заданий — тоді кнопку просто не показуємо. */
  readonly enabled = signal<boolean>(!!environment.googleOAuthClientId);

  /** Вантажить GIS-скрипт один раз на застосунок. */
  load(): Promise<void> {
    if (!this.enabled()) return Promise.reject(new Error('google client id not configured'));
    if (loader) return loader;
    loader = new Promise<void>((resolve, reject) => {
      if (window.google?.accounts?.id) { resolve(); return; }
      const s = document.createElement('script');
      s.src = 'https://accounts.google.com/gsi/client';
      s.async = true;
      s.defer = true;
      s.onload = () => resolve();
      s.onerror = () => reject(new Error('не вдалося завантажити Google Identity Services'));
      document.head.appendChild(s);
    });
    loader.catch(() => { loader = null; });  // дозволяємо повторну спробу
    return loader;
  }

  /**
   * Малює офіційну кнопку Google у переданий контейнер.
   * `onCredential` отримує ID-token, який треба відправити на наш бекенд.
   */
  async renderButton(
    host: HTMLElement,
    onCredential: (idToken: string) => void,
    locale = 'en',
  ): Promise<void> {
    await this.load();
    const gid = window.google?.accounts?.id;
    if (!gid) throw new Error('GIS не ініціалізувався');
    gid.initialize({
      client_id: environment.googleOAuthClientId,
      callback: (res) => onCredential(res.credential),
      cancel_on_tap_outside: true,
    });
    gid.renderButton(host, {
      type: 'standard',
      theme: 'outline',
      size: 'large',
      text: 'continue_with',
      shape: 'rectangular',
      width: 320,
      locale,
    });
  }
}
