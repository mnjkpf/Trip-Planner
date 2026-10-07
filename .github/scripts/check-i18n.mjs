// Перевіряє, що всі мовні файли мають однаковий набір ключів.
// Запускається в CI; падає з переліком розбіжностей.
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';

const dir = 'frontend/web/public/i18n';
const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort();

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
