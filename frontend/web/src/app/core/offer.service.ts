import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { FlightSearchResponse, HotelSearchResponse } from './models';

/** Клієнт для /api/flights і /api/hotels (offer-service через gateway). */
@Injectable({ providedIn: 'root' })
export class OfferService {
  private http = inject(HttpClient);

  /** Пошук авіаквитків; returnDate опційний (one-way). */
  flights(
    from: string,
    to: string,
    depart: string,
    returnDate: string | null,
    adults = 1,
    currency = 'EUR',
  ): Observable<FlightSearchResponse> {
    let params = new HttpParams()
      .set('from', from)
      .set('to', to)
      .set('depart', depart)
      .set('adults', adults)
      .set('currency', currency);
    if (returnDate) params = params.set('returnDate', returnDate);
    return this.http.get<FlightSearchResponse>('/api/flights', { params });
  }

  /** Пошук готелів у місті на вибрані дати. */
  hotels(
    query: string,
    checkIn: string,
    checkOut: string,
    adults = 1,
    currency = 'EUR',
  ): Observable<HotelSearchResponse> {
    const params = new HttpParams()
      .set('q', query)
      .set('checkIn', checkIn)
      .set('checkOut', checkOut)
      .set('adults', adults)
      .set('currency', currency);
    return this.http.get<HotelSearchResponse>('/api/hotels', { params });
  }
}
