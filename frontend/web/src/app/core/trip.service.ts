import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  Budget,
  BudgetRequest,
  CreateTripRequest,
  ExpenseRequest,
  Itinerary,
  PlanJob,
  ShareLink,
  SharedTrip,
  TravelContext,
  Trip,
  UpdateTripRequest,
} from './models';

/** Усе ходить через gateway (проксі /api -> :8080). */
@Injectable({ providedIn: 'root' })
export class TripService {
  private http = inject(HttpClient);

  list(): Observable<Trip[]> {
    return this.http.get<Trip[]>('/api/trips');
  }

  get(id: string): Observable<Trip> {
    return this.http.get<Trip>(`/api/trips/${id}`);
  }

  create(req: CreateTripRequest): Observable<Trip> {
    return this.http.post<Trip>('/api/trips', req);
  }

  update(id: string, req: UpdateTripRequest): Observable<Trip> {
    return this.http.put<Trip>(`/api/trips/${id}`, req);
  }

  remove(id: string): Observable<void> {
    return this.http.delete<void>(`/api/trips/${id}`);
  }

  plan(id: string): Observable<PlanJob> {
    return this.http.post<PlanJob>(`/api/trips/${id}/plan`, {});
  }

  itinerary(id: string): Observable<Itinerary> {
    return this.http.get<Itinerary>(`/api/trips/${id}/itinerary`);
  }

  context(lat: number, lon: number, startDate: string, endDate: string): Observable<TravelContext> {
    const params = new HttpParams()
      .set('lat', lat)
      .set('lon', lon)
      .set('startDate', startDate)
      .set('endDate', endDate);
    return this.http.get<TravelContext>('/api/context', { params });
  }

  // ── редагування маршруту (усі повертають оновлений маршрут) ──
  itinAdd(
    tripId: string,
    dayIndex: number,
    body: { placeId: string; placeName: string; category?: string | null; lat: number; lon: number },
  ): Observable<Itinerary> {
    return this.http.post<Itinerary>(`/api/trips/${tripId}/days/${dayIndex}/items`, body);
  }

  itinRemove(tripId: string, itemId: string): Observable<Itinerary> {
    return this.http.delete<Itinerary>(`/api/trips/${tripId}/items/${itemId}`);
  }

  itinMove(tripId: string, itemId: string, toDayIndex: number, toOrder: number): Observable<Itinerary> {
    return this.http.put<Itinerary>(`/api/trips/${tripId}/items/${itemId}/move`, { toDayIndex, toOrder });
  }

  itinPatch(
    tripId: string,
    itemId: string,
    body: { plannedStart?: string | null; note?: string | null; locked?: boolean },
  ): Observable<Itinerary> {
    return this.http.patch<Itinerary>(`/api/trips/${tripId}/items/${itemId}`, body);
  }

  itinOptimize(tripId: string, dayIndex: number): Observable<Itinerary> {
    return this.http.post<Itinerary>(`/api/trips/${tripId}/days/${dayIndex}/optimize`, {});
  }

  // ── публічне посилання на маршрут ──
  /** Ідемпотентно: якщо посилання вже є, бекенд поверне його ж. */
  share(tripId: string): Observable<ShareLink> {
    return this.http.post<ShareLink>(`/api/trips/${tripId}/share`, {});
  }

  /** 404, якщо подорожжю ще не ділилися. */
  shareCurrent(tripId: string): Observable<ShareLink> {
    return this.http.get<ShareLink>(`/api/trips/${tripId}/share`);
  }

  shareRevoke(tripId: string): Observable<void> {
    return this.http.delete<void>(`/api/trips/${tripId}/share`);
  }

  /** Публічний перегляд — без токена авторизації, працює і для гостя. */
  sharedTrip(token: string): Observable<SharedTrip> {
    return this.http.get<SharedTrip>(`/api/public/trips/${encodeURIComponent(token)}`);
  }

  // ── бюджет (кожна зміна повертає ціле зведення) ──
  budget(tripId: string): Observable<Budget> {
    return this.http.get<Budget>(`/api/trips/${tripId}/budget`);
  }

  setBudget(tripId: string, req: BudgetRequest): Observable<Budget> {
    return this.http.put<Budget>(`/api/trips/${tripId}/budget`, req);
  }

  addExpense(tripId: string, req: ExpenseRequest): Observable<Budget> {
    return this.http.post<Budget>(`/api/trips/${tripId}/expenses`, req);
  }

  updateExpense(tripId: string, expenseId: string, req: ExpenseRequest): Observable<Budget> {
    return this.http.put<Budget>(`/api/trips/${tripId}/expenses/${expenseId}`, req);
  }

  removeExpense(tripId: string, expenseId: string): Observable<Budget> {
    return this.http.delete<Budget>(`/api/trips/${tripId}/expenses/${expenseId}`);
  }
}
