// Генерує src/environments/environment.ts зі змінних оточення.
//
// Потрібно тому, що справжній environment.ts у git не лежить — у ньому ключ
// Google Maps. Локально ти копіюєш environment.example.ts руками, а Vercel і CI
// викликають цей скрипт перед складанням.
import { writeFileSync } from 'node:fs';

const value = (name) => (process.env[name] ?? '').trim();

const file = `// ЗГЕНЕРОВАНО scripts/write-env.mjs — не редагувати вручну.
export const environment = {
  production: true,
  googleMapsApiKey: '${value('GOOGLE_MAPS_API_KEY')}',
  googleOAuthClientId: '${value('GOOGLE_OAUTH_CLIENT_ID')}',
  googleMapsMapId: '${value('GOOGLE_MAPS_MAP_ID')}',
};
`;

writeFileSync('src/environments/environment.ts', file);

const missing = ['GOOGLE_MAPS_API_KEY', 'GOOGLE_MAPS_MAP_ID'].filter((n) => !value(n));
if (missing.length) {
  // Не падаємо: без ключа застосунок працює, просто мапа не намалюється.
  console.warn(`environment.ts створено, але порожні: ${missing.join(', ')}`);
} else {
  console.log('environment.ts створено');
}
