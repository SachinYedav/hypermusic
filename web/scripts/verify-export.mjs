import {access, readFile} from 'node:fs/promises';
const pages = ['index.html','download.html','releases.html','changelog.html','privacy.html','terms.html','copyright.html','404.html','robots.txt','sitemap.xml'];
for (const page of pages) await access(new URL('../out/'+page,import.meta.url));
const html = await readFile(new URL('../out/index.html',import.meta.url),'utf8');
if(!html.includes('Explore HyperMusic features')) throw new Error('Home content missing from exported HTML');
console.log('Verified static export: all public pages and homepage content exist.');
