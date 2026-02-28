/**
 * Fix type declaration paths in angular/dist so consumers resolve www correctly.
 *
 * ng-packagr emits .d.ts with "from '../www/BackgroundGeolocation'".
 * That path is resolved relative to the .d.ts file (in angular/dist/), so it
 * points to angular/www/, which does not exist in the published package.
 * Replacing with '../../www/ makes it resolve to the package root www/ from
 * angular/dist/, so no junction/symlink is needed on Windows.
 */
const path = require('path');
const fs = require('fs');

const distDir = path.join(__dirname, '..', 'angular', 'dist');
if (!fs.existsSync(distDir)) return;

const needFix = (content) => content.includes("'../www/") || content.includes('"../www/');
const fix = (content) =>
  content
    .replace(/'\.\.\/www\//g, "'../../www/")
    .replace(/"\.\.\/www\//g, '"../../www/');

function walk(dir) {
  for (const name of fs.readdirSync(dir)) {
    const full = path.join(dir, name);
    const stat = fs.statSync(full);
    if (stat.isDirectory()) walk(full);
    else if (name.endsWith('.d.ts')) {
      let content = fs.readFileSync(full, 'utf8');
      if (needFix(content)) {
        fs.writeFileSync(full, fix(content), 'utf8');
      }
    }
  }
}

walk(distDir);
