import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { resolve, extname, sep } from 'node:path';
const root=resolve('out');
const types={'.html':'text/html; charset=utf-8','.css':'text/css','.js':'text/javascript','.json':'application/json','.png':'image/png','.svg':'image/svg+xml','.ico':'image/x-icon','.xml':'application/xml','.txt':'text/plain','.woff2':'font/woff2'};
createServer(async(req,res)=>{try{const path=decodeURIComponent(new URL(req.url,'http://localhost').pathname);let file=resolve(root,'.'+path);if(file!==root&&!file.startsWith(root+sep)){res.writeHead(403).end();return;}if(path.endsWith('/'))file=resolve(file,'index.html');else if(!extname(file)){try{if((await stat(file)).isDirectory())file=resolve(file,'index.html');}catch{file+='.html';}}const data=await readFile(file);res.writeHead(200,{'Content-Type':types[extname(file)]||'application/octet-stream'}).end(data);}catch{res.writeHead(404,{'Content-Type':'text/plain'}).end('Not found');}}).listen(4173,'0.0.0.0',()=>console.log('Static preview: http://127.0.0.1:4173'));
