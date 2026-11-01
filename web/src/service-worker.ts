/// <reference lib="webworker" />
import { immutable, assets } from '$app/manifest';
import { version } from '$app/env';

const sw = self as unknown as ServiceWorkerGlobalScope;
const CACHE = `smartedit-${version}`;
const IMMUTABLE: string[] = immutable.map((a) => a.path as string);
const STATIC: string[] = assets.map((a) => a.path as string);
const ASSETS = [...IMMUTABLE, ...STATIC];

sw.addEventListener('install', (e) => { e.waitUntil(caches.open(CACHE).then((c) => c.addAll(STATIC)).then(() => sw.skipWaiting())); });
sw.addEventListener('activate', (e) => {
  e.waitUntil(caches.keys().then((ks) => Promise.all(ks.filter((k) => k !== CACHE).map((k) => caches.delete(k)))).then(() => sw.clients.claim()));
});
// Cache-first for built assets, network-first (falling back to cache) for pages.
sw.addEventListener('fetch', (e) => {
  if (e.request.method !== 'GET') return;
  const url = new URL(e.request.url);
  if (url.origin !== location.origin) return;
  e.respondWith((async () => {
    const cache = await caches.open(CACHE);
    if (ASSETS.includes(url.pathname)) {
      // Hashed build files (pdf.js and its worker are large) are cached the first time they're used, not at install.
      const hit = await cache.match(url.pathname); if (hit) return hit;
      const res = await fetch(e.request); if (res.ok) cache.put(url.pathname, res.clone()); return res;
    }
    try { const res = await fetch(e.request); if (res.ok) cache.put(e.request, res.clone()); return res; }
    catch { const hit = await cache.match(e.request); if (hit) return hit; throw new Error('offline'); }
  })());
});
