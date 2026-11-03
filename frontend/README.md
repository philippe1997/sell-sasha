# Sell Sasha Gallery

React and Vite frontend for the Cloudinary furniture catalog API. The gallery
requests 40 photos at a time, asks the backend to filter by category, and uses
`react-virtuoso` to keep off-screen photo cards out of the DOM.

## Run

```powershell
npm install
Copy-Item .env.example .env.local
npm run dev
```

The dev server runs on http://localhost:5173. Configure `VITE_API_BASE_URL` in
`.env.local` if the Java API is hosted somewhere other than
`http://localhost:8080`.

## Production build

```powershell
npm run build
npm run preview
```

Configure the backend's `CLOUDINARY_CATEGORIES` to match the photo folder names
in Cloudinary. Image API credentials belong only in the backend;
Vite environment variables are public and must never contain secrets.
