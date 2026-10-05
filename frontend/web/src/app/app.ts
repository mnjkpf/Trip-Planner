import { Component, effect, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { TranslatePipe } from '@ngx-translate/core';
import { AuthService } from './core/auth.service';
import { LangService } from './core/lang.service';
import { RequestLogService } from './core/request-log.service';
import { UserService } from './core/user.service';
import { WishlistService } from './core/wishlist.service';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, TranslatePipe],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {
  protected auth = inject(AuthService);
  protected wishlist = inject(WishlistService);
  protected log = inject(RequestLogService);
  protected lang = inject(LangService);
  private users = inject(UserService);
  private router = inject(Router);

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
    this.router.navigate(['/login']);
  }
}
