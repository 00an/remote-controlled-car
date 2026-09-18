import { execFileSync } from 'child_process';
import { readdirSync, statSync } from 'fs';
import { join, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const baseUrl = 'http://localhost:8080/en';
const pagesDir = join(__dirname, '..', 'app', '[locale]');

function getPages(dir, urlPath = '') {
  const urls = [];
  for (const entry of readdirSync(dir)) {
    const full = join(dir, entry);
    if (statSync(full).isDirectory()) {
      // route groups like (auth) dont affect the url
      const next = entry.startsWith('(') ? urlPath : `${urlPath}/${entry}`;
      urls.push(...getPages(full, next));
    } else if (entry === 'page.tsx') {
      urls.push(baseUrl + urlPath);
    }
  }
  return urls;
}

const urls = getPages(pagesDir);
console.log('checking pages:', urls);
// execFileSync (not execSync) so the URL list is passed as discrete argv entries
// rather than interpolated into a shell string — avoids shell-injection if a route
// name ever contains shell metacharacters.
execFileSync('axe', [...urls, '--tags', 'wcag2a,wcag2aa,wcag21a,wcag21aa'], { stdio: 'inherit' });
