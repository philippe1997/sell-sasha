# Sell Sasha Catalog API

Spring Boot 3 REST API for a Cloudinary-backed furniture photo gallery. It
fetches photos from Cloudinary in cursor-based pages and filters a category
before requesting the page, rather than downloading the whole catalog to
filter it in the browser.

## Requirements

- Java 17+
- Cloudinary API credentials with Admin API access

## Configure

Copy `.env.example` to `.env` and set your Cloudinary credentials. The backend
loads `.env` automatically when started from the `backend` directory. You can
also provide these values as environment variables:

```powershell
$env:CLOUDINARY_CLOUD_NAME = "your-cloud-name"
$env:CLOUDINARY_API_KEY = "your-api-key"
$env:CLOUDINARY_API_SECRET = "your-api-secret"
$env:CLOUDINARY_CATEGORIES = "bucătărie,cameră tineret,comode și noptiere,dressing,diverse,dulap sub scară,living,paturi de mijloc"
$env:CLOUDINARY_FOLDER_FIELD = "asset_folder"
$env:FRONTEND_ORIGIN = "http://localhost:5173"
```

Set `CLOUDINARY_CATEGORIES` to the exact Cloudinary folder names used for your
gallery. A selected category searches that folder and its subfolders before
pagination, so unrelated photos are never sent to the browser. The default
`asset_folder` field is for Cloudinary dynamic-folder environments. For a
legacy fixed-folder environment, set `CLOUDINARY_FOLDER_FIELD=folder`.

## Run and test

```powershell
.\mvnw.cmd spring-boot:run
```

```powershell
.\mvnw.cmd test
```

The Maven wrapper downloads Maven automatically. Java 17+ is still required.
The API listens on port 8080 by default. `GET /api/health` works without
Cloudinary credentials.

## Endpoints

- `GET /api/health` — API health check.
- `GET /api/categories` — configured category names.
- `GET /api/photos?limit=40` — first page of all images.
- `GET /api/photos?category=bucătărie&limit=40` — first page in one category.
- `GET /api/photos?category=bucătărie&limit=40&cursor=...` — next page.

Responses include `photos`, `nextCursor`, `category`, and `pageSize`. Pass
`nextCursor` unchanged to get the next page. A null cursor means there are no
more results. Limits are restricted to 1–100 per request. Responses are cached
for one minute, and the cache holds at most 500 pages.

The API returns Cloudinary-generated `f_auto,q_auto` thumbnail and display URLs
so the gallery downloads appropriately sized images instead of full originals.
