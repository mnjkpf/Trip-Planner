import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ChangePasswordRequest, UpdateProfileRequest, UserProfile } from './models';

@Injectable({ providedIn: 'root' })
export class UserService {
  private http = inject(HttpClient);

  me(): Observable<UserProfile> {
    return this.http.get<UserProfile>('/api/user/me');
  }

  update(req: UpdateProfileRequest): Observable<UserProfile> {
    return this.http.put<UserProfile>('/api/user/me', req);
  }

  changePassword(req: ChangePasswordRequest): Observable<void> {
    return this.http.post<void>('/api/user/me/password', req);
  }

  deleteAccount(): Observable<void> {
    return this.http.delete<void>('/api/user/me');
  }
}
