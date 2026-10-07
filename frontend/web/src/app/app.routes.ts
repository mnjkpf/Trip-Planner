import { Routes } from '@angular/router';
import { authGuard } from './core/auth.guard';
import { Login } from './auth/login';
import { Register } from './auth/register';
import { TripsList } from './trips/trips-list';
import { TripCreate } from './trips/trip-create';
import { TripEdit } from './trips/trip-edit';
import { TripDetail } from './trips/trip-detail';
import { SharedTripView } from './trips/shared-trip';
import { TripPrint } from './trips/trip-print';
import { PlaceSearch } from './places/place-search';
import { PlaceDetail } from './places/place-detail';
import { Profile } from './profile/profile';
import { WishlistList } from './wishlist/wishlist-list';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'trips' },
  { path: 'login', component: Login },
  // Публічний маршрут за посиланням — БЕЗ authGuard: сюди приходять гості.
  { path: 's/:token', component: SharedTripView },
  { path: 's/:token/print', component: TripPrint },
  { path: 'register', component: Register },
  { path: 'trips', component: TripsList, canActivate: [authGuard] },
  { path: 'trips/new', component: TripCreate, canActivate: [authGuard] },
  { path: 'trips/:id/edit', component: TripEdit, canActivate: [authGuard] },
  { path: 'trips/:id/print', component: TripPrint, canActivate: [authGuard] },
  { path: 'trips/:id', component: TripDetail, canActivate: [authGuard] },
  { path: 'places', component: PlaceSearch, canActivate: [authGuard] },
  { path: 'places/:id', component: PlaceDetail, canActivate: [authGuard] },
  { path: 'wishlist', component: WishlistList, canActivate: [authGuard] },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
  { path: '**', redirectTo: 'trips' },
];
