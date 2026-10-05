/**
 * Валюти для бюджету. Тримаємо власний короткий список, а не повний ISO 4217:
 * у списку з 180 позицій шукати свою валюту гірше, ніж вписати її руками,
 * а туристичні напрямки покриваються кількома десятками.
 *
 * Назви не перекладаємо — код і символ однакові в усіх мовах.
 */
export interface Currency {
  code: string;
  symbol: string;
}

export const CURRENCIES: Currency[] = [
  { code: 'AED', symbol: 'د.إ' },
  { code: 'AUD', symbol: 'A$' },
  { code: 'BGN', symbol: 'лв' },
  { code: 'CAD', symbol: 'C$' },
  { code: 'CHF', symbol: 'Fr' },
  { code: 'CNY', symbol: '¥' },
  { code: 'CZK', symbol: 'Kč' },
  { code: 'DKK', symbol: 'kr' },
  { code: 'EGP', symbol: 'E£' },
  { code: 'EUR', symbol: '€' },
  { code: 'GBP', symbol: '£' },
  { code: 'GEL', symbol: '₾' },
  { code: 'HUF', symbol: 'Ft' },
  { code: 'IDR', symbol: 'Rp' },
  { code: 'ILS', symbol: '₪' },
  { code: 'INR', symbol: '₹' },
  { code: 'ISK', symbol: 'kr' },
  { code: 'JPY', symbol: '¥' },
  { code: 'KRW', symbol: '₩' },
  { code: 'MAD', symbol: 'د.م.' },
  { code: 'MDL', symbol: 'L' },
  { code: 'MXN', symbol: 'Mex$' },
  { code: 'NOK', symbol: 'kr' },
  { code: 'NZD', symbol: 'NZ$' },
  { code: 'PLN', symbol: 'zł' },
  { code: 'RON', symbol: 'lei' },
  { code: 'RSD', symbol: 'дин' },
  { code: 'SEK', symbol: 'kr' },
  { code: 'SGD', symbol: 'S$' },
  { code: 'THB', symbol: '฿' },
  { code: 'TRY', symbol: '₺' },
  { code: 'UAH', symbol: '₴' },
  { code: 'USD', symbol: '$' },
  { code: 'VND', symbol: '₫' },
  { code: 'ZAR', symbol: 'R' },
];

/** Країни, де валюта не очевидна з коду. Решта — єврозона або fallback. */
const BY_COUNTRY: Record<string, string> = {
  AE: 'AED', AU: 'AUD', BG: 'BGN', CA: 'CAD', CH: 'CHF', CN: 'CNY', CZ: 'CZK',
  DK: 'DKK', EG: 'EGP', GB: 'GBP', GE: 'GEL', HU: 'HUF', ID: 'IDR', IL: 'ILS',
  IN: 'INR', IS: 'ISK', JP: 'JPY', KR: 'KRW', MA: 'MAD', MD: 'MDL', MX: 'MXN',
  NO: 'NOK', NZ: 'NZD', PL: 'PLN', RO: 'RON', RS: 'RSD', SE: 'SEK', SG: 'SGD',
  TH: 'THB', TR: 'TRY', UA: 'UAH', US: 'USD', VN: 'VND', ZA: 'ZAR',
};

/** Єврозона — щоб не вгадувати EUR як сліпий fallback для всього світу. */
const EUROZONE = new Set([
  'AT', 'BE', 'CY', 'DE', 'EE', 'ES', 'FI', 'FR', 'GR', 'HR', 'IE', 'IT',
  'LT', 'LU', 'LV', 'MT', 'NL', 'PT', 'SI', 'SK', 'ME', 'XK', 'AD', 'MC', 'SM', 'VA',
]);

/**
 * Валюта за кодом країни призначення — щоб форма бюджету відкривалась із
 * розумним значенням, а не порожнім полем. null, якщо країна невідома:
 * краще лишити вибір користувачу, ніж тихо підставити не ту валюту.
 */
export function currencyForCountry(countryCode: string | null | undefined): string | null {
  if (!countryCode || countryCode.length !== 2) return null;
  const cc = countryCode.toUpperCase();
  if (BY_COUNTRY[cc]) return BY_COUNTRY[cc];
  if (EUROZONE.has(cc)) return 'EUR';
  return null;
}
