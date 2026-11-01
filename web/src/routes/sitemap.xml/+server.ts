import { abs } from '#lib/site.ts';
import { tools } from '#lib/tools.ts';

export const prerender = true;

export function GET() {
  const paths = ['/', '/tools', ...tools.map((t) => `/tools/${t.slug}`), '/faq', '/contact', '/privacy', '/terms', '/delete-account'];
  const xml = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">\n${paths.map((p) => `  <url><loc>${abs(p)}</loc></url>`).join('\n')}\n</urlset>\n`;
  return new Response(xml, { headers: { 'Content-Type': 'application/xml' } });
}
