import { readFile } from 'node:fs/promises';

const marks = [
  'citizen',
  'advocate',
  'thinker',
  'organizer',
  'volunteer',
  'creator',
  'leader',
  'analyst',
  'visionary',
  'builder'
];

const failures = [];

for (const mark of marks) {
  const url = new URL(`../public/ballot-marks/${mark}.svg`, import.meta.url);
  try {
    const source = await readFile(url, 'utf8');
    if (!source.includes('<svg') || !source.includes('viewBox="0 0 64 64"')) {
      failures.push(`${mark}.svg must use the shared 64x64 SVG viewBox`);
    }
    if (!source.includes('fill="#000"')) {
      failures.push(`${mark}.svg must expose an opaque mask shape`);
    }
    if (/<script\b|<foreignObject\b|\bhref\s*=|\bxlink:href\s*=/i.test(source)) {
      failures.push(`${mark}.svg contains unsupported executable or external content`);
    }
  } catch {
    failures.push(`${mark}.svg is missing`);
  }
}

if (failures.length > 0) {
  console.error(failures.join('\n'));
  process.exit(1);
}

console.log(`Ballot Mark assets valid: ${marks.length} SVG presets`);
