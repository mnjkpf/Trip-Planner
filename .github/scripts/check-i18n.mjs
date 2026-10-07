// Перевіряє, що всі мовні файли мають однаковий набір ключів.
// Запускається в CI; падає з переліком розбіжностей.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

// Шлях рахуємо відносно самого скрипта, щоб працювало з будь-якого cwd
// (у CI крок має working-directory: frontend/web).
const scriptDir = dirname(fileURLToPath(import.meta.url));
const dir = resolve(scriptDir, '..', '..', 'frontend', 'web', 'public', 'i18n');

if (!existsSync(dir)) {
  console.log(`[check-i18n] Пропускаю: папки ${dir} немає`);
  process.exit(0);
}

const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();
if (files.length === 0) {
  console.log('[check-i18n] Пропускаю: немає .json файлів');
  process.exit(0);
}

const flatten = (obj, prefix = '') =>
  Object.entries(obj).flatMap(([key, value]) =>
    value !== null && typeof value === 'object'
      ? flatten(value, `${prefix}${key}.`)
      : [`${prefix}${key}`],
  );

const keysByFile = new Map(
  files.map((f) => [f, new Set(flatten(JSON.parse(readFileSync(join(dir, f), 'utf8'))))]),
);

const [reference, referenceKeys] = [...keysByFile][0];
let failed = false;

for (const [file, keys] of keysByFile) {
  const missing = [...referenceKeys].filter((k) => !keys.has(k));
  const extra = [...keys].filter((k) => !referenceKeys.has(k));
  if (missing.length || extra.length) {
    failed = true;
    console.error(`${file}: бракує ${missing.length}, зайвих ${extra.length}`);
    missing.slice(0, 20).forEach((k) => console.error(`  - ${k}`));
    extra.slice(0, 20).forEach((k) => console.error(`  + ${k}`));
  }
}

console.log(`${files.length} мов, ${referenceKeys.size} ключів (еталон: ${reference})`);
process.exit(failed ? 1 : 0);
