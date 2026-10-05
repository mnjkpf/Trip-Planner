export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

/**
 * Побажання до планування — УСІ поля опційні. Порожній об'єкт = плануємо на
 * дефолтах бекенда, тож користувач може не торкатися жодного фільтра.
 */
export interface PlanPreferences {
  pace?: 'RELAXED' | 'BALANCED' | 'PACKED' | null;
  /** Категорії POI (PARK, MUSEUM, …), які підняти наперед при доборі місць. */
  interests?: string[] | null;
  searchRadiusM?: number | null;
  /** "HH:mm" */
  dayStartTime?: string | null;
}

export type TripRole = 'OWNER' | 'EDITOR' | 'VIEWER';

export interface Trip {
  id: string;
  userId: string;
  title: string;
  destinationName: string;
  destinationCountry: string | null;
  destinationLat: number;
  destinationLon: number;
  originAirport: string | null;
  startDate: string;
  endDate: string;
  status: 'DRAFT' | 'PLANNING' | 'PLANNED' | 'ARCHIVED';
  preferences: PlanPreferences | null;
  /** Роль того, хто запитує: по ній ховаємо кнопки редагування. */
  role: TripRole;
  createdAt: string;
}

export interface CreateTripRequest {
  title: string;
  destinationName: string;
  destinationCountry?: string;
  destinationLat: number;
  destinationLon: number;
  originAirport?: string;
  startDate: string;
  endDate: string;
  preferences?: PlanPreferences;
}

export interface PlanJob {
  jobId: string;
  tripId: string;
  status: string;
  requestedAt: string;
}

export interface ItineraryItem {
  id: string;
  order: number;
  placeId: string;
  placeName: string;
  placeCategory: string | null;
  lat: number;
  lon: number;
  time: string | null;
  dwellMinutes: number;
  travelMinutesFromPrev: number | null;
  locked: boolean;
  note: string | null;
}

export interface ItineraryDay {
  dayIndex: number;
  date: string;
  distanceKm: number;
  walkMinutes: number;
  items: ItineraryItem[];
}

export interface Itinerary {
  tripId: string;
  status: string;
  days: ItineraryDay[];
}

export interface WeatherDay {
  date: string;
  tempMinC: number | null;
  tempMaxC: number | null;
  precipitationMm: number | null;
}

export interface TravelContext {
  destinationLat: number;
  destinationLon: number;
  season: string;
  /** Текст англійською — фолбек, коли код невідомий фронту. */
  climateHint: string;
  /** Машинний код підказки (COOL_RAINY, HOT, …) — його і перекладаємо. */
  climateHintCode: string | null;
  days: WeatherDay[];
}

export interface Place {
  id: string;
  name: string;
  category: string;
  description: string | null;
  lat: number;
  lon: number;
  city: string | null;
  countryCode: string | null;
  address: string | null;
  imageUrl: string | null;
  website: string | null;
}

// Редагування шле ті самі поля, що й створення.
export type UpdateTripRequest = CreateTripRequest;

export interface WishlistItem {
  id: string;
  placeId: string;
  placeName: string;
  placeLat: number | null;
  placeLon: number | null;
  note: string | null;
  createdAt: string;
}

export interface WishlistItemRequest {
  placeId: string;
  placeName: string;
  placeLat?: number;
  placeLon?: number;
  note?: string;
}

export interface CitySuggestion {
  name: string;
  country: string | null;
  countryCode: string | null;
  formatted: string;
  lat: number;
  lon: number;
}

export interface Airport {
  iata: string;
  name: string;
  city: string;
  country: string;
  lat: number;
  lon: number;
}

export interface FlightSegment {
  departureAirport: string;
  departureAirportName: string;
  departureTime: string;
  arrivalAirport: string;
  arrivalAirportName: string;
  arrivalTime: string;
  durationMinutes: number;
  airline: string;
  airlineLogoUrl: string | null;
  flightNumber: string | null;
  travelClass: string | null;
  airplane: string | null;
}

export interface FlightOffer {
  segments: FlightSegment[];
  totalDurationMinutes: number;
  layoverCount: number;
  price: number;
  currency: string;
  type: string | null;
  bookingToken: string | null;
  carbonEmissionsGrams: number | null;
}

export interface FlightSearchResponse {
  best: FlightOffer[];
  other: FlightOffer[];
}

export interface HotelOffer {
  id: string | null;
  name: string;
  type: string | null;
  link: string | null;
  lat: number | null;
  lon: number | null;
  rating: number | null;
  reviewsCount: number | null;
  hotelClass: number | null;
  amenities: string[];
  imageUrls: string[];
  totalRate: number | null;
  ratePerNight: number | null;
  currency: string;
  checkInTime: string | null;
  checkOutTime: string | null;
  description: string | null;
}

export interface HotelSearchResponse {
  properties: HotelOffer[];
}

export interface OwnPlacePreview {
  name: string;
  lat: number;
  lon: number;
  source: string;
  originalUrl: string;
}

export interface UserProfile {
  id: string;
  email: string;
  displayName: string | null;
  preferredLanguage: string | null;
  oauthProvider: string | null;
  role: string;
  createdAt: string;
}

export interface UpdateProfileRequest {
  displayName?: string | null;
  preferredLanguage?: string | null;
}

export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
}

/** Активне публічне посилання на маршрут. path — відносний, домен доклеює фронт. */
export interface ShareLink {
  token: string;
  path: string;
  createdAt: string;
}

export interface SharedTripItem {
  order: number;
  placeName: string;
  placeCategory: string | null;
  lat: number;
  lon: number;
  time: string | null;
  dwellMinutes: number;
  travelMinutesFromPrev: number | null;
  note: string | null;
}

export interface SharedTripDay {
  dayIndex: number;
  date: string;
  distanceKm: number;
  walkMinutes: number;
  items: SharedTripItem[];
}

/** Маршрут очима гостя: вужче за Trip — ні id, ні власника, ні вішліста. */
export interface SharedTrip {
  title: string;
  destinationName: string;
  destinationCountry: string | null;
  destinationLat: number;
  destinationLon: number;
  startDate: string;
  endDate: string;
  days: SharedTripDay[];
}

export type ExpenseCategory =
  | 'FLIGHT'
  | 'HOTEL'
  | 'FOOD'
  | 'TRANSPORT'
  | 'ACTIVITY'
  | 'SHOPPING'
  | 'OTHER';

export interface Expense {
  id: string;
  category: ExpenseCategory;
  title: string;
  amount: number;
  currency: string;
  /** null = витрата на всю подорож, а не на конкретний день. */
  spentOn: string | null;
  note: string | null;
  createdAt: string;
}

export interface ExpenseRequest {
  category: ExpenseCategory;
  title: string;
  amount: number;
  currency: string;
  spentOn?: string | null;
  note?: string | null;
}

export interface CurrencyTotal {
  currency: string;
  amount: number;
}

export interface CategoryTotal {
  category: ExpenseCategory;
  currency: string;
  amount: number;
}

/**
 * Зведення бюджету. spent і remaining рахуються ЛИШЕ для валюти плану —
 * курсів у системі немає, тож інші валюти живуть окремо в totals.
 */
export interface Budget {
  plannedAmount: number | null;
  plannedCurrency: string | null;
  spent: number | null;
  remaining: number | null;
  totals: CurrencyTotal[];
  byCategory: CategoryTotal[];
  expenses: Expense[];
}

export interface BudgetRequest {
  amount: number | null;
  currency?: string | null;
}

export interface TripMember {
  id: string;
  userId: string;
  email: string;
  role: TripRole;
  /** true для того, хто дивиться список. */
  self: boolean;
  createdAt: string;
}

export interface InviteMemberRequest {
  email: string;
  role: 'EDITOR' | 'VIEWER';
}
