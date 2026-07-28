import { readFile } from 'node:fs/promises';

const requiredDomains = [
  'common',
  'auth',
  'ballots',
  'profile',
  'admin',
  'comments',
  'notifications',
  'errors'
];

async function load(locale) {
  const url = new URL(`../src/shared/i18n/messages/${locale}.json`, import.meta.url);
  return JSON.parse(await readFile(url, 'utf8'));
}

function flatten(value, prefix = '') {
  return Object.entries(value).flatMap(([key, child]) => {
    const path = prefix ? `${prefix}.${key}` : key;
    return child && typeof child === 'object' && !Array.isArray(child)
      ? flatten(child, path)
      : [[path, child]];
  });
}

function placeholders(message) {
  return [...message.matchAll(/\{(\w+)\}/g)].map(match => match[1]).sort();
}

const [vi, en] = await Promise.all([load('vi'), load('en')]);
const failures = [];

for (const domain of requiredDomains) {
  if (!(domain in vi)) failures.push(`vi is missing domain: ${domain}`);
  if (!(domain in en)) failures.push(`en is missing domain: ${domain}`);
}

const viEntries = new Map(flatten(vi));
const enEntries = new Map(flatten(en));

for (const [key, value] of viEntries) {
  if (!enEntries.has(key)) failures.push(`en is missing key: ${key}`);
  if (typeof value !== 'string' || value.trim() === '') failures.push(`vi has an invalid message: ${key}`);
  const enValue = enEntries.get(key);
  if (typeof value === 'string' && typeof enValue === 'string') {
    const viPlaceholders = placeholders(value).join(',');
    const enPlaceholders = placeholders(enValue).join(',');
    if (viPlaceholders !== enPlaceholders) {
      failures.push(`placeholder mismatch for ${key}: vi=[${viPlaceholders}] en=[${enPlaceholders}]`);
    }
  }
}

for (const [key, value] of enEntries) {
  if (!viEntries.has(key)) failures.push(`vi is missing key: ${key}`);
  if (typeof value !== 'string' || value.trim() === '') failures.push(`en has an invalid message: ${key}`);
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`i18n catalogs valid: ${viEntries.size} matching messages across ${requiredDomains.length} domains`);
