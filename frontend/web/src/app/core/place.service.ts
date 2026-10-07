import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Airport, CitySuggestion, OwnPlacePreview, Place } from './models';

@Injectable({ providedIn: 'root' })
export class PlaceService {
  private http = inject(HttpClient);

  search(lat: number, lon: number, radius: number, category?: string): Observable<Place[]> {
    let params = new HttpParams().set('lat', lat).set('lon', lon).set('radius', radius);
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<Place[]>('/api/places/search', { params });
  }

  get(id: string): Observable<Place> {
    return this.http.get<Place>(`/api/places/${id}`);
  }

  /** Підказки міст для форми створення подорожі (ключ провайдера — на сервері). */
  geocode(q: string, limit = 5): Observable<CitySuggestion[]> {
    const params = new HttpParams().set('q', q).set('limit', limit);
    return this.http.get<CitySuggestion[]>('/api/places/geocode', { params });
  }

  /** Автодоповнення аеропорту за IATA/містом/назвою. */
  airports(q: string, limit = 6): Observable<Airport[]> {
    const params = new HttpParams().set('q', q).set('limit', limit);
    return this.http.get<Airport[]>('/api/places/airports', { params });
  }

  /** Найближчі аеропорти до координат (для auto-suggest destination airport). */
  nearestAirport(lat: number, lon: number, limit = 3): Observable<Airport[]> {
    const params = new HttpParams().set('lat', lat).set('lon', lon).set('limit', limit);
    return this.http.get<Airport[]>('/api/places/airports/nearest', { params });
  }

  /** Розгортає посилання Google Maps у preview (координати + назва). */
  resolveLink(url: string): Observable<OwnPlacePreview> {
    return this.http.post<OwnPlacePreview>('/api/places/resolve-link', { url });
  }
}
