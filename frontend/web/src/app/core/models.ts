export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
}

export interface Trip {
  id: string;
  userId: string;
  title: string;
  destinationName: string;
  destinationCountry: string | null;
  destinationLat: number;
  destinationLon: number;
  startDate: string;
  endDate: string;
  status: 'DRAFT' | 'PLANNING' | 'PLANNED' | 'ARCHIVED';
  createdAt: string;
}

export interface CreateTripRequest {
  title: string;
  destinationName: string;
  destinationCountry?: string;
  destinationLat: number;
  destinationLon: number;
  startDate: string;
  endDate: string;
}

export interface PlanJob {
  jobId: string;
  tripId: string;
  status: string;
  requestedAt: string;
}

export interface ItineraryItem {
  order: number;
  placeId: string;
  placeName: string;
  placeCategory: string | null;
  lat: number;
  lon: number;
}

export interface ItineraryDay {
  dayIndex: number;
  date: string;
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
  climateHint: string;
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
