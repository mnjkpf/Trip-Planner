import { Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DatePipe } from '@angular/common';
import { WishlistService } from '../core/wishlist.service';

@Component({
  selector: 'app-wishlist-list',
  imports: [RouterLink, DatePipe],
  template: `
    <div class="page-head">
      <h1>Вішліст</h1>
      <a class="link" routerLink="/places">+ Знайти місця</a>
    </div>

    @if (wishlist.loading()) {
      <p class="muted">Завантаження…</p>
    } @else if (wishlist.items().length === 0) {
      <div class="card empty">Вішліст порожній. Збережи місця зі сторінки «Місця» — вони зʼявляться тут.</div>
    } @else {
      <div class="grid places-grid">
        @for (w of wishlist.items(); track w.placeId) {
          <div class="card wish-card">
            <h3><a [routerLink]="['/places', w.placeId]">{{ w.placeName }}</a></h3>
            @if (w.note) { <p class="muted">{{ w.note }}</p> }
            <p class="dates">Додано {{ w.createdAt | date: 'd MMM y' }}</p>
            <button class="btn-sm danger" (click)="remove(w.placeId)">Прибрати</button>
          </div>
        }
      </div>
    }
  `,
})
export class WishlistList {
  protected wishlist = inject(WishlistService);

  constructor() {
    this.wishlist.load(true); // відкрили сторінку — тягнемо свіжий список
  }

  remove(placeId: string): void {
    this.wishlist.remove(placeId).subscribe();
  }
}
