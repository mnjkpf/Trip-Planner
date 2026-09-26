import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Login } from './auth/login';
import { Register } from './auth/register';
import { TripsList } from './trips/trips-list';
import { TripCreate } from './trips/trip-create';
import { TripEdit } from './trips/trip-edit';
import { TripDetail } from './trips/trip-detail';
import { PlaceSearch } from './places/place-search';
import { PlaceDetail } from './places/place-detail';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'trips' },
  { path: 'login', component: Login },
  { path: 'register', component: Register },
  { path: 'trips', component: TripsList, canActivate: [authGuard] },
  { path: 'trips/new', component: TripCreate, canActivate: [authGuard] },
  { path: 'trips/:id/edit', component: TripEdit, canActivate: [authGuard] },
  { path: 'trips/:id', component: TripDetail, canActivate: [authGuard] },
  { path: 'places', component: PlaceSearch, canActivate: [authGuard] },
  { path: 'places/:id', component: PlaceDetail, canActivate: [authGuard] },
  { path: '**', redirectTo: 'trips' },
];
