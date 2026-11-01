// One place for anything that changes when the custom domain is bought.
export const SITE = {
  name: 'Smart Edit',
  // Set to the real origin once the domain is connected, e.g. 'https://smartedit.com'. Empty = relative URLs only.
  origin: 'https://smartedit-2000.web.app',
  tagline: 'Scan, convert and edit documents. Private by design.',
  contactEmail: ''
};
export const abs = (path: string) => SITE.origin + path;
