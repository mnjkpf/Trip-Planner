import { Component, computed, model, signal } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';
import { PlanPreferences } from '../core/models';

/** Група інтересів у UI → категорії POI, якими її розкриваємо для бекенда. */
interface InterestGroup {
  key: string;
  categories: string[];
}

const GROUPS: InterestGroup[] = [
  { key: 'nature', categories: ['PARK', 'BEACH'] },
  { key: 'culture', categories: ['MUSEUM', 'ATTRACTION'] },
  { key: 'food', categories: ['RESTAURANT', 'CAFE'] },
  { key: 'nightlife', categories: ['BAR'] },
  { key: 'shopping', categories: ['SHOP'] },
];

export const INTEREST_GROUPS: readonly InterestGroup[] = GROUPS;

const PACES = ['RELAXED', 'BALANCED', 'PACKED'] as const;
const RADII = [2000, 5000, 15000];
const DAY_STARTS = ['08:00', '09:00', '11:00'];

/**
 * Опційні фільтри планування. Нічого не обов'язкове: поки користувач не чіпав
 * блок, value() === {} і запит іде без поля preferences — бекенд планує як завжди.
 *
 * Двостороння привʼязка: <app-plan-preferences [(value)]="prefs" />
 */
@Component({
  selector: 'app-plan-preferences',
  imports: [TranslatePipe],
  template: `
    <div class="prefs">
      <button type="button" class="prefs-head" (click)="open.set(!open())">
        <span class="prefs-sign">{{ open() ? '−' : '+' }}</span>
        <span class="prefs-label">
          {{ 'prefs.title' | translate }}
          <span class="prefs-opt mono">{{ 'prefs.optional' | translate }}</span>
        </span>
        @if (chosenCount() > 0) {
          <span class="prefs-count mono">{{ chosenCount() }}</span>
        }
      </button>

      @if (open()) {
        <div class="prefs-body">
          <p class="prefs-intro">{{ 'prefs.intro' | translate }}</p>

          <div class="pgroup">
            <div class="plabel">{{ 'prefs.pace' | translate }}</div>
            <div class="chips">
              @for (p of paces; track p) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="value().pace === p"
                  (click)="togglePace(p)"
                >
                  {{ 'prefs.pace_' + p.toLowerCase() | translate }}
                </button>
              }
            </div>
            <div class="phint">{{ 'prefs.pace_hint' | translate }}</div>
          </div>

          <div class="pgroup">
            <div class="plabel">{{ 'prefs.interests' | translate }}</div>
            <div class="chips">
              @for (g of groups; track g.key) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="hasGroup(g.key)"
                  (click)="toggleGroup(g.key)"
                >
                  {{ 'prefs.interest_' + g.key | translate }}
                </button>
              }
            </div>
            <div class="phint">{{ 'prefs.interests_hint' | translate }}</div>
          </div>

          <div class="pgroup">
            <div class="plabel">{{ 'prefs.radius' | translate }}</div>
            <div class="chips">
              @for (r of radii; track r) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="value().searchRadiusM === r"
                  (click)="toggleRadius(r)"
                >
                  {{ 'prefs.radius_' + r | translate }}
                </button>
              }
            </div>
          </div>

          <div class="pgroup">
            <div class="plabel">{{ 'prefs.day_start' | translate }}</div>
            <div class="chips">
              @for (t of dayStarts; track t) {
                <button
                  type="button"
                  class="chip"
                  [class.on]="value().dayStartTime === t"
                  (click)="toggleDayStart(t)"
                >
                  {{ 'prefs.day_start_' + t.slice(0, 2) | translate }}
                </button>
              }
            </div>
          </div>

          @if (chosenCount() > 0) {
            <button type="button" class="prefs-reset mono" (click)="reset()">
              {{ 'prefs.reset' | translate }}
            </button>
          }
        </div>
      }
    </div>
  `,
  styles: [`
    .prefs { border: 2px solid var(--divider); }
    .prefs-head {
      display: flex; align-items: center; gap: 10px; width: 100%;
      background: transparent; border: 0; cursor: pointer;
      padding: 11px 12px; text-align: left;
    }
    .prefs-head:hover { background: var(--accent-100); }
    .prefs-sign {
      flex: none; width: 18px; height: 18px; display: grid; place-items: center;
      background: var(--ink); color: var(--bg); font: 800 13px/1 var(--font);
    }
    .prefs-label { font: 800 13px/1.2 var(--font); color: var(--ink); flex: 1; }
    .prefs-opt {
      font-size: 10px; font-weight: 400; color: var(--muted);
      text-transform: uppercase; letter-spacing: 0.06em; margin-left: 8px;
    }
    .prefs-count {
      flex: none; background: var(--accent); color: #fff;
      font-size: 10px; padding: 2px 7px;
    }

    .prefs-body {
      border-top: 2px solid var(--divider);
      padding: 14px 12px; display: flex; flex-direction: column; gap: 16px;
    }
    .prefs-intro { margin: 0; font-size: 11px; color: var(--muted); }

    .pgroup { display: flex; flex-direction: column; gap: 7px; }
    .plabel {
      font: 800 10px/1 var(--font); text-transform: uppercase;
      letter-spacing: 0.08em; color: var(--muted);
    }
    .chips { display: flex; flex-wrap: wrap; gap: 6px; }
    .chip {
      background: transparent; border: 2px solid var(--divider); cursor: pointer;
      padding: 6px 11px; font: 700 12px/1 var(--font); color: var(--ink);
    }
    .chip:hover { border-color: var(--ink); }
    .chip.on { background: var(--ink); border-color: var(--ink); color: var(--bg); }
    .phint { font-size: 10px; color: var(--muted); }

    .prefs-reset {
      align-self: flex-start; background: transparent; border: 0; cursor: pointer;
      padding: 0; font-size: 10px; color: var(--muted);
      text-transform: uppercase; letter-spacing: 0.06em; text-decoration: underline;
    }
    .prefs-reset:hover { color: var(--accent); }
  `],
})
export class PlanPreferencesEditor {
  /** Двосторонній сигнал із батьком. {} = нічого не вибрано. */
  value = model<PlanPreferences>({});

  readonly groups = GROUPS;
  readonly paces = PACES;
  readonly radii = RADII;
  readonly dayStarts = DAY_STARTS;

  open = signal(false);

  chosenCount = computed(() => {
    const v = this.value();
    let n = 0;
    if (v.pace) n++;
    if (v.interests && v.interests.length > 0) n++;
    if (v.searchRadiusM) n++;
    if (v.dayStartTime) n++;
    return n;
  });

  /** Група «активна», якщо вибрані всі її категорії. */
  hasGroup(key: string): boolean {
    const cats = GROUPS.find((g) => g.key === key)?.categories ?? [];
    const chosen = this.value().interests ?? [];
    return cats.length > 0 && cats.every((c) => chosen.includes(c));
  }

  toggleGroup(key: string): void {
    const cats = GROUPS.find((g) => g.key === key)?.categories ?? [];
    const chosen = this.value().interests ?? [];
    const next = this.hasGroup(key)
      ? chosen.filter((c) => !cats.includes(c))
      : [...chosen, ...cats.filter((c) => !chosen.includes(c))];
    this.patch({ interests: next.length > 0 ? next : null });
  }

  togglePace(p: string): void {
    this.patch({ pace: this.value().pace === p ? null : (p as PlanPreferences['pace']) });
  }

  toggleRadius(r: number): void {
    this.patch({ searchRadiusM: this.value().searchRadiusM === r ? null : r });
  }

  toggleDayStart(t: string): void {
    this.patch({ dayStartTime: this.value().dayStartTime === t ? null : t });
  }

  reset(): void {
    this.value.set({});
  }

  private patch(part: Partial<PlanPreferences>): void {
    this.value.set({ ...this.value(), ...part });
  }
}

/**
 * Прибирає порожні поля. undefined означає «не надсилати preferences взагалі»,
 * щоб бекенд не чіпав уже збережені побажання.
 */
export function cleanPreferences(v: PlanPreferences | null | undefined): PlanPreferences | undefined {
  if (!v) return undefined;
  const out: PlanPreferences = {};
  if (v.pace) out.pace = v.pace;
  if (v.interests && v.interests.length > 0) out.interests = v.interests;
  if (v.searchRadiusM) out.searchRadiusM = v.searchRadiusM;
  if (v.dayStartTime) out.dayStartTime = v.dayStartTime;
  return Object.keys(out).length > 0 ? out : undefined;
}

/** Нормалізує те, що приходить із бекенда, у форму для редактора. */
export function toEditablePreferences(v: PlanPreferences | null | undefined): PlanPreferences {
  if (!v) return {};
  return {
    pace: v.pace ?? null,
    interests: v.interests && v.interests.length > 0 ? [...v.interests] : null,
    searchRadiusM: v.searchRadiusM ?? null,
    dayStartTime: v.dayStartTime ? v.dayStartTime.slice(0, 5) : null,
  };
}

/**
 * Повний стан для PUT: кожне поле присутнє, незняті фільтри — null/[].
 * Так бекенд може СТЕРТИ раніше вибраний фільтр, а не лише додати новий.
 */
export function fullPreferences(v: PlanPreferences | null | undefined): PlanPreferences {
  return {
    pace: v?.pace ?? null,
    interests: v?.interests && v.interests.length > 0 ? [...v.interests] : [],
    searchRadiusM: v?.searchRadiusM ?? null,
    dayStartTime: v?.dayStartTime ?? null,
  };
}

/**
 * Ключі перекладу для показу вибраних побажань «тільки читати» (сторінка подорожі).
 * Нестандартні значення (наприклад радіус, вибраний через API) просто пропускаємо —
 * краще не показати чип, ніж показати сирий ключ.
 */
export function preferenceChipKeys(p: PlanPreferences | null | undefined): string[] {
  if (!p) return [];
  const out: string[] = [];
  if (p.pace && (PACES as readonly string[]).includes(p.pace)) {
    out.push('prefs.pace_' + p.pace.toLowerCase());
  }
  const chosen = p.interests ?? [];
  for (const g of GROUPS) {
    if (g.categories.some((c) => chosen.includes(c))) out.push('prefs.interest_' + g.key);
  }
  if (p.searchRadiusM && RADII.includes(p.searchRadiusM)) {
    out.push('prefs.radius_' + p.searchRadiusM);
  }
  const start = p.dayStartTime ? p.dayStartTime.slice(0, 5) : null;
  if (start && DAY_STARTS.includes(start)) {
    out.push('prefs.day_start_' + start.slice(0, 2));
  }
  return out;
}
