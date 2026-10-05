/// <reference types="google.maps" />
import { environment } from '../../environments/environment';

/**
 * Єдиний модуль, що завантажує Google Maps JS SDK і дає тонкі хелпери для
 * мапи, маркерів і ліній у стилі застосунку. Усі сторінки з картою мають
 * імпортувати тільки звідси — ключ і Map ID описані в одному місці.
 *
 * Чому динамічний loader, а не <script> у index.html: тоді ключ не живе в
 * HTML, а підтягується з environment.ts, і SDK не вантажиться для сторінок
 * без карти (авторизація, вішліст, журнал подій).
 */

const PURPLE = '#5b2cff';
const PURPLE_DARK = '#4318d9';

let loader: Promise<typeof google> | null = null;

/** Лінива ініціалізація Google Maps SDK. Повертає ту ж обіцянку при повторах. */
export function loadMaps(): Promise<typeof google> {
  if (loader) return loader;
  const p = new Promise<typeof google>((resolve, reject) => {
    const w = window as unknown as { google?: typeof google };
    if (w.google?.maps) {
      resolve(w.google);
      return;
    }
    const s = document.createElement('script');
    const params = new URLSearchParams({
      key: environment.googleMapsApiKey,
      v: 'weekly',
      libraries: 'marker',
    });
    s.src = 'https://maps.googleapis.com/maps/api/js?' + params.toString();
    s.async = true;
    s.defer = true;
    s.onload = () => {
      const g = (window as unknown as { google?: typeof google }).google;
      if (g?.maps) resolve(g);
      else reject(new Error('google.maps не ініціалізувався'));
    };
    s.onerror = () => reject(new Error('не вдалося завантажити Google Maps SDK'));
    document.head.appendChild(s);
  });
  // Якщо завантажити не вдалось — забуваємо проміс, щоб наступна сторінка з картою мала шанс.
  p.catch(() => { loader = null; });
  loader = p;
  return loader;
}

/**
 * Стандартна мапа застосунку — Vector + Map ID зі стилем з Cloud Console.
 * Опції (zoom-кнопки, перемикач типу карти) ввімкнені — Google ховає їх на
 * малих екранах сам.
 */
export function createMap(
  el: HTMLElement,
  center: google.maps.LatLngLiteral,
  zoom: number,
): google.maps.Map {
  return new google.maps.Map(el, {
    center,
    zoom,
    mapId: environment.googleMapsMapId,
    disableDefaultUI: false,
    mapTypeControl: true,
    mapTypeControlOptions: {
      style: google.maps.MapTypeControlStyle.DROPDOWN_MENU,
      position: google.maps.ControlPosition.TOP_RIGHT,
    },
    streetViewControl: false,
    fullscreenControl: false,
    zoomControl: true,
    zoomControlOptions: { position: google.maps.ControlPosition.LEFT_TOP },
    clickableIcons: false,
    gestureHandling: 'greedy',
  });
}

/** Маленьке фіолетове коло — ставимо на одиничне місце (сторінка «деталі»). */
export function dotMarker(
  map: google.maps.Map,
  pos: google.maps.LatLngLiteral,
  title?: string,
): google.maps.marker.AdvancedMarkerElement {
  const el = document.createElement('div');
  el.className = 'wl-dot';
  el.style.cssText =
    'width:18px;height:18px;border-radius:50%;background:' +
    PURPLE +
    ';border:3px solid #fff;box-shadow:0 1px 4px rgba(0,0,0,0.35);';
  return new google.maps.marker.AdvancedMarkerElement({
    map,
    position: pos,
    title,
    content: el,
  });
}

/** Нумерований пін для маршруту (круг з цифрою). */
export function numberedMarker(
  map: google.maps.Map,
  pos: google.maps.LatLngLiteral,
  order: number,
  title?: string,
): google.maps.marker.AdvancedMarkerElement {
  const el = document.createElement('div');
  el.className = 'wl-pin';
  el.textContent = String(order);
  el.style.cssText =
    'display:inline-flex;align-items:center;justify-content:center;' +
    'width:28px;height:28px;background:' +
    PURPLE +
    ';color:#fff;border:2px solid #fff;font:800 13px/1 "Archivo",system-ui,sans-serif;' +
    'box-shadow:0 2px 6px rgba(0,0,0,0.35);';
  return new google.maps.marker.AdvancedMarkerElement({
    map,
    position: pos,
    title,
    content: el,
  });
}


/** Один DirectionsService на застосунок — лінивий. */
let directions: google.maps.DirectionsService | null = null;
function dirService(): google.maps.DirectionsService {
  if (!directions) directions = new google.maps.DirectionsService();
  return directions;
}

/**
 * Будує маршрут по дорогах через Google Directions API. Для масиву з n точок
 * бере перший як origin, останній як destination, решту — як waypoints
 * (ліміт 10 без PRO-тарифу, цього більше ніж достатньо для денного маршруту).
 * Повертає геометрію як масив LatLng — його можна передати у Polyline.
 * Якщо Google відмовив (over quota / ZERO_RESULTS) — повертає null, викликач
 * сам вирішує, малювати пряму лінію чи ні.
 */
export async function routeByRoads(
  points: google.maps.LatLngLiteral[],
  travelMode: google.maps.TravelMode = google.maps.TravelMode.WALKING,
): Promise<google.maps.LatLng[] | null> {
  if (points.length < 2) return null;
  const safe = points.slice(0, 11);           // 1 origin + 9 waypoints + 1 destination
  const origin = safe[0];
  const destination = safe[safe.length - 1];
  const waypoints = safe.slice(1, -1).map((p) => ({ location: p, stopover: true }));
  try {
    const res = await dirService().route({ origin, destination, waypoints, travelMode });
    const path = res.routes[0]?.overview_path ?? null;
    if (!path) console.warn('[routeByRoads] Directions OK але overview_path порожній');
    return path;
  } catch (e: unknown) {
    // .code тут = DirectionsStatus: REQUEST_DENIED (API не ввімкнений),
    // ZERO_RESULTS (маршруту немає), OVER_QUERY_LIMIT (квота) тощо.
    const code = (e as { code?: string })?.code ?? e;
    console.warn('[routeByRoads] Directions відмовив:', code, e);
    return null;
  }
}

/** Пунктирна фіолетова лінія між точками маршруту. */
export function routePolyline(
  map: google.maps.Map,
  path: google.maps.LatLngLiteral[],
): google.maps.Polyline {
  return new google.maps.Polyline({
    map,
    path,
    strokeColor: PURPLE_DARK,
    strokeOpacity: 0,
    icons: [
      {
        icon: { path: 'M 0,-1 0,1', strokeOpacity: 0.9, scale: 3 },
        offset: '0',
        repeat: '12px',
      },
    ],
  });
}

/** Акуратний fitBounds із відступом. Якщо точка одна — просто центрує. */
export function fit(
  map: google.maps.Map,
  points: google.maps.LatLngLiteral[],
  singleZoom = 14,
): void {
  if (points.length === 0) return;
  if (points.length === 1) {
    map.setCenter(points[0]);
    map.setZoom(singleZoom);
    return;
  }
  const b = new google.maps.LatLngBounds();
  points.forEach((p) => b.extend(p));
  map.fitBounds(b, 60);
}

/** Прибирає маркери й лінії з мапи — зручно перед перемальовуванням дня. */
export function clearOverlays(
  overlays: (google.maps.marker.AdvancedMarkerElement | google.maps.Polyline)[],
): void {
  for (const o of overlays) {
    if ('setMap' in o) o.setMap(null);
    else (o as google.maps.marker.AdvancedMarkerElement).map = null;
  }
}

/** Мітка статичного маркера: Google дозволяє рівно один символ A–Z або 0–9. */
function staticLabel(order: number): string {
  if (order >= 1 && order <= 9) return String(order);
  const letter = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'[order - 10];
  return letter ?? '';
}

/** Координата для URL: 5 знаків — це ~1 м, більше в URL лише забирає місце. */
function coord(lat: number, lon: number): string {
  return lat.toFixed(5) + ',' + lon.toFixed(5);
}

/**
 * Рівномірно проріджує лінію маршруту до max точок, завжди лишаючи першу й
 * останню. Потрібно, бо Directions повертає сотні точок геометрії, а URL
 * статичної мапи обмежений (~16 КБ) — та й зайва деталізація на друку не видна.
 */
function thin<T>(points: T[], max: number): T[] {
  if (points.length <= max) return points;
  const step = (points.length - 1) / (max - 1);
  const out: T[] = [];
  for (let i = 0; i < max; i++) out.push(points[Math.round(i * step)]);
  return out;
}

/**
 * URL картинки для ДРУКУ (Maps Static API). Інтерактивна мапа на друк не йде:
 * SDK малює тайли ліниво й лише для видимої області, тож у PDF виходить
 * порожній прямокутник. Статична картинка — звичайний <img>, друкується як є.
 *
 * Потребує окремо увімкненого "Maps Static API" на тому ж ключі.
 */
export function staticMapUrl(
  stops: google.maps.LatLngLiteral[],
  routePath: google.maps.LatLng[] | null,
  size = { w: 640, h: 360 },
): string {
  const params = new URLSearchParams();
  params.set('size', `${size.w}x${size.h}`);
  params.set('scale', '2');                   // ретина: на друку 96→192 dpi
  params.set('maptype', 'roadmap');
  params.set('language', document.documentElement.lang || 'en');
  params.set('key', environment.googleMapsApiKey);

  // Лінія маршруту — перед маркерами, щоб піни лягли зверху.
  const line = routePath && routePath.length > 1
    ? thin(routePath, 60).map((p) => coord(p.lat(), p.lng()))
    : thin(stops, 60).map((p) => coord(p.lat, p.lng));
  if (line.length > 1) {
    params.append('path', `color:0x4318d9ff|weight:4|${line.join('|')}`);
  }

  stops.forEach((p, i) => {
    const label = staticLabel(i + 1);
    const style = label ? `color:0x5b2cff|label:${label}` : 'color:0x5b2cff';
    params.append('markers', `${style}|${coord(p.lat, p.lng)}`);
  });

  // URLSearchParams кодує | як %7C — Google це приймає.
  return 'https://maps.googleapis.com/maps/api/staticmap?' + params.toString();
}
