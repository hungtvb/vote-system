import { access, readFile } from 'node:fs/promises';
import { constants } from 'node:fs';

const required = [
  'out/ballot-mark.svg',
  'out/manifest.webmanifest',
  'out/index.html'
];

await Promise.all(required.map(path => access(path, constants.R_OK)));

const [html, manifestText] = await Promise.all([
  readFile('out/index.html', 'utf8'),
  readFile('out/manifest.webmanifest', 'utf8')
]);
const manifest = JSON.parse(manifestText);

if (!html.includes('/ballot-mark.svg')) {
  throw new Error('Static HTML does not declare the Ballot Edition icon.');
}
if (!html.includes('/manifest.webmanifest')) {
  throw new Error('Static HTML does not declare the web manifest.');
}
if (!Array.isArray(manifest.icons) || !manifest.icons.some(icon => icon.src === '/ballot-mark.svg')) {
  throw new Error('Web manifest does not declare the Ballot Edition icon.');
}

console.log('Ballot Edition favicon and manifest assets verified.');
