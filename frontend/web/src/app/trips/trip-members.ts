import { Component, OnInit, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { TranslatePipe } from '@ngx-translate/core';
import { Trip, TripMember } from '../core/models';
import { TripService } from '../core/trip.service';

/**
 * Учасники подорожі. Склад змінює лише власник; решта бачить список і може
 * піти сама. Список приходить із бекенда цілим після кожної зміни — локально
 * нічого не перераховуємо.
 */
@Component({
  selector: 'app-trip-members',
  imports: [FormsModule, TranslatePipe],
  template: `
    <div class="members">
      <div class="kicker">{{ 'members.title' | translate }}</div>

      @if (isOwner()) {
        <div class="invite">
          <input
            type="email"
            [placeholder]="'members.email' | translate"
            [(ngModel)]="email"
            name="email"
            (keyup.enter)="invite()"
          />
          <select [(ngModel)]="role" name="role">
            <option value="EDITOR">{{ 'members.role_editor' | translate }}</option>
            <option value="VIEWER">{{ 'members.role_viewer' | translate }}</option>
          </select>
          <button class="btn btn-primary btn-sm" (click)="invite()" [disabled]="busy() || !email.trim()">
            {{ 'members.invite' | translate }}
          </button>
        </div>
        <p class="hint">{{ 'members.hint' | translate }}</p>
      }

      @if (error()) { <p class="error">{{ error()! | translate }}</p> }

      <div class="list">
        @for (m of members(); track m.id) {
          <div class="row">
            <span class="r-mail">
              {{ m.email || ('members.unknown_email' | translate) }}
              @if (m.self) { <span class="you">{{ 'members.you' | translate }}</span> }
            </span>

            @if (isOwner() && m.role !== 'OWNER') {
              <select class="r-role" [ngModel]="m.role" (ngModelChange)="setRole(m, $event)" [disabled]="busy()">
                <option value="EDITOR">{{ 'members.role_editor' | translate }}</option>
                <option value="VIEWER">{{ 'members.role_viewer' | translate }}</option>
              </select>
            } @else {
              <span class="r-badge">{{ 'members.role_' + m.role.toLowerCase() | translate }}</span>
            }

            @if (canRemove(m)) {
              <button class="r-del" (click)="remove(m)" [disabled]="busy()">
                {{ m.self ? ('members.leave' | translate) : ('common.delete' | translate) }}
              </button>
            } @else {
              <span class="r-spacer"></span>
            }
          </div>
        }
      </div>
    </div>
  `,
  styles: [`
    .members { display: flex; flex-direction: column; gap: 10px; }
    .invite { display: flex; gap: 8px; flex-wrap: wrap; }
    .invite input { flex: 1 1 220px; min-width: 180px; }
    .hint { font-size: 11px; color: var(--muted); margin: 0; }

    .list { display: flex; flex-direction: column; }
    .row {
      display: flex; align-items: center; gap: 10px;
      padding: 8px 0; border-bottom: 1px solid var(--divider-soft);
    }
    .r-mail { flex: 1; min-width: 0; font-size: 13px; overflow: hidden; text-overflow: ellipsis; }
    .you { font-size: 10px; color: var(--muted); margin-left: 6px; text-transform: uppercase; letter-spacing: 0.06em; }
    .r-role { flex: none; width: 130px; }
    .r-badge {
      flex: none; width: 130px; text-align: center; font-size: 10px;
      text-transform: uppercase; letter-spacing: 0.05em;
      background: var(--accent-100); color: var(--accent-800); padding: 4px 6px;
    }
    .r-del {
      flex: none; background: none; border: 0; cursor: pointer; padding: 0 2px;
      font-size: 11px; color: var(--muted); text-decoration: underline;
    }
    .r-del:hover { color: #d92d20; }
    .r-spacer { flex: none; width: 1px; }
  `],
})
export class TripMembers implements OnInit {
  readonly trip = input.required<Trip>();
  /** Вихід із чужої подорожі — батько має прибрати її зі списку й піти геть. */
  readonly left = output<void>();

  private trips = inject(TripService);

  members = signal<TripMember[]>([]);
  busy = signal(false);
  error = signal<string | null>(null);

  email = '';
  role: 'EDITOR' | 'VIEWER' = 'EDITOR';

  ngOnInit(): void {
    this.trips.members(this.trip().id).subscribe({
      next: (m) => this.members.set(m),
      error: () => this.error.set('members.error'),
    });
  }

  /** Поля ще може не бути, якщо бекенд старіший за фронт — тоді це автор. */
  isOwner(): boolean {
    return (this.trip().role ?? 'OWNER') === 'OWNER';
  }

  /** Власника не прибирає ніхто; решту — власник, або сам учасник («вийти»). */
  canRemove(m: TripMember): boolean {
    return m.role !== 'OWNER' && (this.isOwner() || m.self);
  }

  invite(): void {
    const email = this.email.trim();
    if (!email || this.busy()) return;
    this.busy.set(true);
    this.error.set(null);
    this.trips.invite(this.trip().id, { email, role: this.role }).subscribe({
      next: (m) => {
        this.members.set(m);
        this.email = '';
        this.busy.set(false);
      },
      error: (e: HttpErrorResponse) => {
        // 404 — такої пошти немає, 409 — уже має доступ: різні повідомлення
        this.error.set(e?.status === 409 ? 'members.already' : 'members.not_found');
        this.busy.set(false);
      },
    });
  }

  setRole(m: TripMember, role: 'EDITOR' | 'VIEWER'): void {
    this.busy.set(true);
    this.trips.changeRole(this.trip().id, m.id, role).subscribe({
      next: (list) => {
        this.members.set(list);
        this.busy.set(false);
      },
      error: () => {
        this.error.set('members.error');
        this.busy.set(false);
      },
    });
  }

  remove(m: TripMember): void {
    this.busy.set(true);
    this.trips.removeMember(this.trip().id, m.id).subscribe({
      next: () => {
        this.busy.set(false);
        if (m.self) {
          this.left.emit();
        } else {
          this.members.update((list) => list.filter((x) => x.id !== m.id));
        }
      },
      error: () => {
        this.error.set('members.error');
        this.busy.set(false);
      },
    });
  }
}
