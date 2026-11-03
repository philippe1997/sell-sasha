# Sell Sasha photo gallery

The new full-stack gallery is split into two independent projects:

- [backend/](./backend/README.md) — Java 17 and Spring Boot API. It requests
  cursor-paginated images from Cloudinary and filters categories at the API
  before returning a page.
- [frontend/](./frontend/README.md) — React and Vite gallery. It fetches the
  next 40 images as you scroll and virtualizes the grid so off-screen cards
  aren't kept in the DOM.

The older app and API folders remain unchanged.

## Requirements

- Java 17+
- Node.js 20.19+ or 22.12+
- Cloudinary API credentials with Admin API access

## Start locally (Windows PowerShell)

In the `backend` terminal, set Cloudinary credentials and start Spring Boot.
Java 17+ is required; the Maven wrapper will download Maven for you:

```powershell
cd backend
$env:CLOUDINARY_CLOUD_NAME = "your-cloud-name"
$env:CLOUDINARY_API_KEY = "your-api-key"
$env:CLOUDINARY_API_SECRET = "your-api-secret"
$env:CLOUDINARY_CATEGORIES = "bucătărie,cameră tineret,comode și noptiere,dressing,diverse,dulap sub scară,living,paturi de mijloc"
$env:CLOUDINARY_FOLDER_FIELD = "asset_folder"
.\mvnw.cmd spring-boot:run
```

In a second terminal, start the gallery:

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. The API health check is at
http://localhost:8080/api/health.

## Category setup

Categories map to Cloudinary folder names. Set `CLOUDINARY_CATEGORIES` to the
folder names used by your photos. Selecting a category calls
`GET /api/photos?category=bucătărie&limit=40`; Cloudinary filters the folder and
its subfolders before pagination, and the next page uses its `next_cursor`.

The API keeps a short in-memory cache for repeated page requests. The browser
also uses a query cache, transformed responsive image URLs, lazy image decoding,
and virtualized rendering. Cloudinary credentials stay on the backend and must
not be added to frontend environment variables.

See the project READMEs for backend and frontend configuration, endpoints, and
production build commands.
