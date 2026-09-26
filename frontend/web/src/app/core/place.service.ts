import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Place } from './models';

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
}
