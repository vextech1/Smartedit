# Smart Edit Web: architecture

**Principle:** every tool runs in the browser. No uploads, no accounts (v1), no server code.

## Stack
SvelteKit (static adapter, prerendered) + TypeScript, deployed to Firebase Hosting. PWA via `src/service-worker.ts`.
Config lives in `web/vite.config.ts` (this SvelteKit version has no `svelte.config.js`). Internal imports use `#lib/...`
(package.json `imports`) with explicit file extensions.

## Layout
- `src/lib/site.ts`: name, canonical origin (set when the custom domain is bought), URL helper.
- `src/lib/tools.ts`: tool registry. One entry adds a page, nav/home card and sitemap URL.
- `src/lib/core/`: framework-free logic (format, image, pdf). Unit-tested in Node (`web/tests`).
- `src/lib/components/`: `FileDrop`, `ImageTool`, `PdfTool`.
- `src/routes/`: pages. Legal/FAQ pages were migrated from `public/` as static HTML.

## Cutover (not done yet)
`public/` still serves the live site. To switch: set `firebase.json` `public` to `web/build`, add `npm ci && npm run build`
to `firebase-hosting.yml`, set `SITE.origin`, and port the `/vendor` + `/js` cache headers (`/_app/immutable` is hashed).

## Roadmap
1 PDF suite (DONE: organize, split, PDF to images) -> 2 Compressor + OCR + Image editor -> 3 Scanner -> 4 Workspace (Files, Library, search, trash in IndexedDB/OPFS) -> 5 i18n, analytics, ads, a11y/perf audit.
Open decision: password-protected PDFs (MuPDF WASM is AGPL; qpdf-wasm is an alternative).
