# Sell Sasha Gallery

React and Vite frontend for the Cloudinary furniture catalog API. The gallery
requests 10 photos initially and 20 photos on each subsequent page, asks the
backend to filter by category, and uses `react-virtuoso` to keep off-screen
photo cards out of the DOM. Images in the virtualized viewport load eagerly,
while off-screen cards load lazily. The first three visible images receive high
fetch priority; other visible images load eagerly at normal priority. The
virtualizer renders an extra viewport ahead to give lazy-loaded images time to
load during scrolling. Changing category resets the gallery's virtualized list
and scroll position. The compressed hero image is preloaded to reduce the
largest first-load download.

## Run

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

The dev server runs on http://localhost:5173. Configure `VITE_API_BASE_URL` in
`.env.local` if the Java API is hosted somewhere other than
`http://localhost:8080`.

## Deploy to Vercel

Deploy the frontend and backend as separate Vercel projects. In the frontend
project's environment variables, set:

- `VITE_API_BASE_URL` to the backend deployment origin, for example
  `https://sell-sasha-backend.vercel.app` (no `/api` suffix).
- `VITE_BASE_PATH` to `/` when serving the frontend from the Vercel domain root.

In the backend project's environment variables, set `FRONTEND_ORIGIN` to the
frontend deployment origin, for example `https://sell-sasha-frontend.vercel.app`.
Use the exact frontend origin without a trailing slash. Add the Cloudinary
credentials to the backend project only.

Vite embeds `VITE_*` values during the build, so redeploy the frontend after
changing its environment variables. Verify the backend at
`https://<backend-domain>/api/health`.

The category list is bundled with the frontend, so rendering the filter does
not require a categories API request. Keep `frontend/src/categories.js` in sync
with the backend's `CLOUDINARY_CATEGORIES` setting; the backend validates
selected categories before querying Cloudinary. Photo pages are cached for five
minutes at Vercel's CDN and in the browser.

## Production build

```powershell
npm run build
npm run preview
```

Configure the backend's `CLOUDINARY_CATEGORIES` to match the photo folder names
in Cloudinary. Image API credentials belong only in the backend;
Vite environment variables are public and must never contain secrets.
