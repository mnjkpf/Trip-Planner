import { inject } from '@angular/core';
import { Routes } from '@angular/router';
<<<<<<< HEAD
import { inject } from '@angular/core';
import { authGuard, guestOkGuard } from './core/auth.guard';
=======
import { authGuard } from './core/auth.guard';
>>>>>>> cfb7641145ba42713c39f5e19b1c6f811f378d3f
import { AuthService } from './core/auth.service';
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

/** Гість починає з каталогу місць, залогінений — зі своїх подорожей. */
const home = () => (inject(AuthService).isLoggedIn() ? '/trips' : '/places');

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: home },
  { path: 'login', component: Login },
  { path: 'register', component: Register },

  // Публічні маршрути — БЕЗ authGuard: сюди приходять гості.
  { path: 's/:token', component: SharedTripView },
  { path: 's/:token/print', component: TripPrint },
  // Каталог місць читається без акаунта: нема чого приховувати, і це
  // єдина сторінка, яку варто показати до реєстрації.
  { path: 'places', component: PlaceSearch },
  { path: 'places/:id', component: PlaceDetail },

  { path: 'trips', component: TripsList, canActivate: [authGuard] },
  // Єдина захищена сторінка, відкрита гостю: тут заводиться тимчасовий
  // акаунт, і далі звичайний authGuard уже пропускає його всюди.
  { path: 'trips/new', component: TripCreate, canActivate: [guestOkGuard] },
  { path: 'trips/:id/edit', component: TripEdit, canActivate: [authGuard] },
  { path: 'trips/:id/print', component: TripPrint, canActivate: [authGuard] },
  { path: 'trips/:id', component: TripDetail, canActivate: [authGuard] },
<<<<<<< HEAD
  // Каталог місць читається без акаунта: нема чого приховувати, і це
  // єдина сторінка, яку варто показати до реєстрації.
  { path: 'places', component: PlaceSearch },
  { path: 'places/:id', component: PlaceDetail },
  { path: 'wishlist', component: WishlistList, canActivate: [authGuard] },
  { path: 'profile', component: Profile, canActivate: [authGuard] },
=======
  { path: 'wishlist', component: WishlistList, canActivate: [authGuard] },
  { path: 'profile', component: Profile, canActivate: [authGuard] },

>>>>>>> cfb7641145ba42713c39f5e19b1c6f811f378d3f
  { path: '**', redirectTo: home },
];
