import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateTripRequest, Itinerary, PlanJob, TravelContext, Trip, UpdateTripRequest } from './models';

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
}
