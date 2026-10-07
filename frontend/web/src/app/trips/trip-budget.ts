import { Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { DatePipe, DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TranslatePipe } from '@ngx-translate/core';
import { CURRENCIES, currencyForCountry } from '../core/currencies';
import { Budget, Expense, ExpenseCategory, Trip } from '../core/models';
import { TripService } from '../core/trip.service';

const CATEGORIES: ExpenseCategory[] = [
  'FLIGHT',
  'HOTEL',
  'FOOD',
  'TRANSPORT',
  'ACTIVITY',
  'SHOPPING',
  'OTHER',
];

/** Витрати одного дня (або «на всю подорож», коли date === null). */
interface ExpenseGroup {
  date: string | null;
  expenses: Expense[];
}

/**
 * Таб «Бюджет». Усі зміни повертають ціле зведення з бекенда, тож локально
 * нічого не перераховуємо — один сигнал budget() і є єдиною правдою.
 */
@Component({
  selector: 'app-trip-budget',
  imports: [FormsModule, DatePipe, DecimalPipe, TranslatePipe],
  template: `
    @if (budget(); as b) {
      <div class="budget">
        <!-- план і прогрес -->
        <section class="plan">
          <div class="plan-form">
            <div class="field">
              <label>{{ 'budget.planned' | translate }}</label>
              <div class="plan-inputs">
                <input type="number" min="0" step="10" [(ngModel)]="planAmount" name="planAmount" />
                <select class="cur" [(ngModel)]="planCurrency" name="planCurrency">
                  @for (c of currencies; track c.code) {
                    <option [value]="c.code">{{ c.code }} {{ c.symbol }}</option>
                  }
                </select>
                <button class="btn btn-secondary btn-sm" (click)="savePlan()" [disabled]="busy()">
                  {{ 'common.save' | translate }}
                </button>
                @if (b.plannedAmount !== null) {
                  <button class="btn-link" (click)="clearPlan()" [disabled]="busy()">
                    {{ 'budget.clear_plan' | translate }}
                  </button>
                }
              </div>
            </div>
          </div>

          @if (b.plannedAmount !== null && b.spent !== null) {
            <div class="bar-wrap">
              <div class="bar" [class.over]="over()">
                <div class="bar-fill" [style.width.%]="progress()"></div>
              </div>
              <div class="bar-legend">
                <span class="spent">
                  {{ b.spent | number: '1.0-2' }} {{ b.plannedCurrency }}
                  <span class="muted">{{ 'budget.of' | translate }} {{ b.plannedAmount | number: '1.0-2' }}</span>
                </span>
                <span class="rest" [class.over]="over()">
                  @if (over()) {
                    {{ 'budget.over_by' | translate }} {{ -(b.remaining ?? 0) | number: '1.0-2' }} {{ b.plannedCurrency }}
                  } @else {
                    {{ 'budget.left' | translate }} {{ b.remaining | number: '1.0-2' }} {{ b.plannedCurrency }}
                  }
                </span>
              </div>
            </div>
          }

          @if (otherTotals().length > 0) {
            <div class="others">
              <span class="others-label">{{ 'budget.other_currencies' | translate }}</span>
              @for (t of otherTotals(); track t.currency) {
                <span class="o-chip mono">{{ t.amount | number: '1.0-2' }} {{ t.currency }}</span>
              }
            </div>
          }
        </section>

        <!-- розбивка по категоріях -->
        @if (b.byCategory.length > 0) {
          <section class="cats">
            <div class="kicker">{{ 'budget.by_category' | translate }}</div>
            @for (c of b.byCategory; track c.category + c.currency) {
              <div class="cat-row">
                <span class="cat-name">{{ 'budget.cat_' + c.category.toLowerCase() | translate }}</span>
                <span class="cat-bar">
                  <span class="cat-fill" [style.width.%]="catShare(c.amount, c.currency)"></span>
                </span>
                <span class="cat-sum mono">{{ c.amount | number: '1.0-2' }} {{ c.currency }}</span>
              </div>
            }
          </section>
        }

        <!-- додати витрату -->
        <section class="add">
          <div class="kicker">{{ 'budget.add' | translate }}</div>
          <div class="add-grid">
            <select [(ngModel)]="form.category" name="category">
              @for (c of categories; track c) {
                <option [value]="c">{{ 'budget.cat_' + c.toLowerCase() | translate }}</option>
              }
            </select>
            <input [placeholder]="'budget.title' | translate" [(ngModel)]="form.title" name="title" />
            <input type="number" min="0" step="1" [placeholder]="'budget.amount' | translate"
                   [(ngModel)]="form.amount" name="amount" />
            <select class="cur" [(ngModel)]="form.currency" name="currency">
              @for (c of currencies; track c.code) {
                <option [value]="c.code">{{ c.code }} {{ c.symbol }}</option>
              }
            </select>
            <input type="date" [(ngModel)]="form.spentOn" name="spentOn" [min]="trip().startDate" [max]="trip().endDate" />
            <button class="btn btn-primary btn-sm" (click)="add()" [disabled]="busy() || !canAdd()">
              {{ 'common.add' | translate }}
            </button>
          </div>
          <p class="hint">{{ 'budget.day_hint' | translate }}</p>
        </section>

        @if (error()) { <p class="error">{{ error()! | translate }}</p> }

        <!-- список витрат -->
        @if (b.expenses.length === 0) {
          <p class="muted empty">{{ 'budget.empty' | translate }}</p>
        } @else {
          @for (g of groups(); track g.date ?? 'trip') {
            <section class="group">
              <div class="g-head">
                @if (g.date) {
                  {{ g.date | date: 'EEEE, d MMM' }}
                } @else {
                  {{ 'budget.whole_trip' | translate }}
                }
              </div>
              @for (e of g.expenses; track e.id) {
                <div class="row">
                  <span class="r-cat">{{ 'budget.cat_' + e.category.toLowerCase() | translate }}</span>
                  <span class="r-title">{{ e.title }}@if (e.note) { <span class="r-note">{{ e.note }}</span> }</span>
                  <span class="r-sum mono">{{ e.amount | number: '1.0-2' }} {{ e.currency }}</span>
                  <button class="r-del" (click)="remove(e)" [disabled]="busy()" [title]="'common.delete' | translate">×</button>
                </div>
              }
            </section>
          }
        }
      </div>
    } @else if (error()) {
      <p class="error">{{ error()! | translate }}</p>
    } @else {
      <p class="muted">{{ 'common.loading' | translate }}</p>
    }
  `,
  styles: [`
    .budget { display: flex; flex-direction: column; gap: 22px; padding: 18px 20px; }

    .plan-inputs { display: flex; gap: 8px; align-items: center; flex-wrap: wrap; }
    .plan-inputs input { width: 140px; }
    .plan-inputs .cur { width: 96px; }
    .btn-link {
      background: none; border: 0; padding: 0; cursor: pointer; font-size: 11px;
      color: var(--muted); text-decoration: underline;
    }
    .btn-link:hover { color: var(--accent); }

    .bar-wrap { margin-top: 14px; }
    .bar { height: 10px; background: var(--surface); border: 2px solid var(--ink); }
    .bar-fill { height: 100%; background: var(--accent); }
    .bar.over .bar-fill { background: #d92d20; }
    .bar-legend { display: flex; justify-content: space-between; gap: 12px; margin-top: 6px; font-size: 12px; }
    .spent { font-weight: 700; }
    .rest { color: var(--muted); }
    .rest.over { color: #d92d20; font-weight: 700; }

    .others { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-top: 12px; }
    .others-label { font-size: 10px; text-transform: uppercase; letter-spacing: 0.06em; color: var(--muted); }
    .o-chip { background: var(--accent-100); color: var(--accent-800); padding: 2px 7px; font-size: 11px; }

    .cat-row { display: flex; align-items: center; gap: 10px; margin-top: 7px; }
    .cat-name { flex: 0 0 120px; font-size: 12px; }
    .cat-bar { flex: 1; height: 8px; background: var(--surface); border: 1px solid var(--divider); min-width: 60px; }
    .cat-fill { display: block; height: 100%; background: var(--accent); }
    .cat-sum { flex: none; font-size: 11px; color: var(--muted); }

    .add-grid { display: flex; gap: 8px; flex-wrap: wrap; margin-top: 8px; }
    .add-grid input, .add-grid select { flex: 0 1 auto; }
    .add-grid input[type='number'] { width: 110px; }
    .add-grid .cur { width: 96px; }
    .hint { font-size: 11px; color: var(--muted); margin: 6px 0 0; }

    .group { border-top: 2px solid var(--divider); padding-top: 10px; }
    .g-head {
      font: 800 10px/1 var(--font); text-transform: uppercase;
      letter-spacing: 0.08em; color: var(--muted); margin-bottom: 8px;
    }
    .row { display: flex; align-items: center; gap: 10px; padding: 7px 0; border-bottom: 1px solid var(--divider-soft); }
    .r-cat {
      flex: 0 0 96px; font-size: 10px; text-transform: uppercase; letter-spacing: 0.05em;
      color: var(--accent-800); background: var(--accent-100); padding: 3px 6px; text-align: center;
    }
    .r-title { flex: 1; min-width: 0; font-size: 13px; font-weight: 600; }
    .r-note { display: block; font-weight: 400; font-size: 11px; color: var(--muted); }
    .r-sum { flex: none; font-size: 13px; font-weight: 700; }
    .r-del {
      flex: none; background: none; border: 0; cursor: pointer;
      font-size: 18px; line-height: 1; color: var(--muted); padding: 0 4px;
    }
    .r-del:hover { color: #d92d20; }
    .empty { padding: 4px 0; }

    /* На телефоні фіксовані 96px під категорію й числа праворуч стискають
       назву до пари літер — краще віддати назві весь рядок. */
    @media (max-width: 560px) {
      .budget { padding: 14px 14px; }
      .row { flex-wrap: wrap; gap: 6px; }
      .r-cat { order: 1; flex: 0 0 auto; }
      .r-sum { order: 2; margin-left: auto; }
      .r-del { order: 3; }
      .r-title { order: 4; flex: 1 0 100%; }
      .cat-name { flex: 0 0 86px; font-size: 11px; }
      .add-grid > * { flex: 1 1 100%; }
      .add-grid input[type='number'], .add-grid .cur { flex: 1 1 calc(50% - 4px); width: auto; }
      .plan-inputs input, .plan-inputs .cur { flex: 1 1 calc(50% - 4px); width: auto; }
    }
  `],
})
export class TripBudget implements OnInit {
  readonly trip = input.required<Trip>();
  private trips = inject(TripService);

  readonly categories = CATEGORIES;
  readonly currencies = CURRENCIES;

  budget = signal<Budget | null>(null);
  busy = signal(false);
  private currencyTouched = false;
  error = signal<string | null>(null);

  planAmount: number | null = null;
  planCurrency = '';
  form: { category: ExpenseCategory; title: string; amount: number | null; currency: string; spentOn: string } = {
    category: 'FOOD',
    title: '',
    amount: null,
    currency: '',
    spentOn: '',
  };

  /** Витрати «на всю подорож» ідуть окремою групою попереду — так їх віддає бекенд. */
  groups = computed<ExpenseGroup[]>(() => {
    const expenses = this.budget()?.expenses ?? [];
    const out: ExpenseGroup[] = [];
    for (const e of expenses) {
      const last = out[out.length - 1];
      if (last && last.date === e.spentOn) last.expenses.push(e);
      else out.push({ date: e.spentOn, expenses: [e] });
    }
    return out;
  });

  progress = computed(() => {
    const b = this.budget();
    if (!b || !b.plannedAmount || b.spent === null) return 0;
    return Math.min((b.spent / b.plannedAmount) * 100, 100);
  });

  over = computed(() => (this.budget()?.remaining ?? 0) < 0);

  /** Валюти, яких немає в плані — показуємо окремо, бо не конвертуємо. */
  otherTotals = computed(() => {
    const b = this.budget();
    if (!b) return [];
    return b.totals.filter((t) => t.currency !== b.plannedCurrency);
  });

  /**
   * Саме ngOnInit, а не конструктор: обовʼязковий input прив'язується вже після
   * створення компонента, і читання trip() у конструкторі падає з NG0950.
   */
  ngOnInit(): void {
    // Валюта за країною призначення — щоб форма відкривалась із розумним
    // значенням. Те, що прийде з бекенда, усе одно перекриє цей здогад.
    const guess = currencyForCountry(this.trip().destinationCountry) ?? 'EUR';
    this.planCurrency = guess;
    this.form.currency = guess;
    this.load();
  }

  canAdd(): boolean {
    return this.form.title.trim().length > 0
      && this.form.amount !== null
      && this.form.amount >= 0
      && /^[a-zA-Z]{3}$/.test(this.form.currency.trim());
  }

  /** Частка категорії від усіх витрат У ТІЙ САМІЙ валюті — інакше бар брехав би. */
  catShare(amount: number, currency: string): number {
    const total = this.budget()?.totals.find((t) => t.currency === currency)?.amount ?? 0;
    return total > 0 ? (amount / total) * 100 : 0;
  }

  savePlan(): void {
    const amount = this.planAmount;
    if (amount === null || amount < 0) return;
    this.apply(
      this.trips.setBudget(this.trip().id, {
        amount,
        currency: this.planCurrency.trim() || this.budget()?.plannedCurrency || null,
      }),
    );
  }

  clearPlan(): void {
    this.planAmount = null;
    this.apply(this.trips.setBudget(this.trip().id, { amount: null }));
  }

  add(): void {
    if (!this.canAdd()) return;
    this.apply(
      this.trips.addExpense(this.trip().id, {
        category: this.form.category,
        title: this.form.title.trim(),
        amount: this.form.amount!,
        currency: this.form.currency.trim().toUpperCase(),
        spentOn: this.form.spentOn || null,
      }),
      () => {
        this.form.title = '';
        this.form.amount = null;
      },
    );
  }

  remove(e: Expense): void {
    this.apply(this.trips.removeExpense(this.trip().id, e.id));
  }

  private load(): void {
    this.trips.budget(this.trip().id).subscribe({
      next: (b) => this.accept(b),
      error: () => this.error.set('budget.error'),
    });
  }

  private apply(obs: ReturnType<TripService['budget']>, after?: () => void): void {
    this.busy.set(true);
    this.error.set(null);
    obs.subscribe({
      next: (b) => {
        this.accept(b);
        this.busy.set(false);
        after?.();
      },
      error: (e: HttpErrorResponse) => {
        // 403 — роль понизили, поки сторінка була відкрита: кнопки ще на місці,
        // а прав уже немає. Загальне «не вдалося» тут нічого не пояснює.
        this.error.set(e?.status === 403 ? 'common.forbidden' : 'budget.error');
        this.busy.set(false);
      },
    });
  }

  /** Поля форми підтягуємо під те, що прийшло: валюту вгадувати користувачу не треба. */
  private accept(b: Budget): void {
    this.budget.set(b);
    this.planAmount = b.plannedAmount;
    this.planCurrency = b.plannedCurrency ?? this.planCurrency;
    // Валюту форми підтягуємо лише раз, при першому завантаженні: інакше після
    // кожної збереженої витрати селект стрибав би назад на валюту плану, хоча
    // користувач свідомо вибрав іншу.
    if (!this.currencyTouched && b.plannedCurrency) {
      this.form.currency = b.plannedCurrency;
      this.currencyTouched = true;
    }
  }
}
