import { error } from '@sveltejs/kit';
import { toolBySlug, tools } from '#lib/tools.ts';

export const entries = () => tools.map((t) => ({ slug: t.slug }));

export function load({ params }) {
  const tool = toolBySlug(params.slug);
  if (!tool) error(404, 'Tool not found');
  return { tool };
}
