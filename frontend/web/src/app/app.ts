import { Component, computed, effect, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { filter, map, startWith } from 'rxjs';
import { AuthService } from './core/auth.service';
import { LangService } from './core/lang.service';
import { UserService } from './core/user.service';
import { WishlistService } from './core/wishlist.service';

/** Сторінки, що показуються на весь екран — без сайдбара. */
const STANDALONE_PREFIXES = ['/login', '/register'];

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected auth = inject(AuthService);
  protected wishlist = inject(WishlistService);
  protected lang = inject(LangService);
  private users = inject(UserService);
  private router = inject(Router);

  /** Поточний URL як сигнал — щоб шаблон знав, чи малювати оболонку. */
  private url = toSignal(
    this.router.events.pipe(
      filter((e): e is NavigationEnd => e instanceof NavigationEnd),
      map((e) => e.urlAfterRedirects),
      startWith(this.router.url),
    ),
    { initialValue: this.router.url },
  );

  /** Логін/реєстрація та друк — на весь екран; решта — в оболонці з сайдбаром. */
  protected standalone = computed(() => {
    const path = this.url().split('?')[0];
    return STANDALONE_PREFIXES.some((p) => path.startsWith(p)) || path.endsWith('/print');
  });

  constructor() {
    // як тільки користувач залогінений — тягнемо вішліст для лічильника в сайдбарі
    effect(() => {
      if (this.auth.isLoggedIn()) {
        this.wishlist.load();
        // після логіну підтягуємо збережену мову користувача з бекенду
        this.users.me().subscribe({
          next: (u) => {
            if (u.preferredLanguage && u.preferredLanguage !== this.lang.current()) {
              this.lang.use(u.preferredLanguage);
            }
          },
          error: () => { /* профіль не критичний для роботи */ },
        });
      }
    });
  }

  onLangChange(ev: Event): void {
    const code = (ev.target as HTMLSelectElement).value;
    this.lang.use(code);
  }

  logout(): void {
    this.auth.logout();
    this.router.navigate(['/places']);
  }
}
