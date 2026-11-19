# Sell Sasha Catalog API

Spring Boot 3 REST API for the furniture photo gallery. Image metadata is
stored in the Supabase `public.photos` table and WebP files are stored in the
private `gallery-images` Storage bucket. The backend proxies image downloads,
so Supabase credentials and private storage paths are never exposed to browsers.

## Requirements

- Java 17+
- A Supabase project

## Configure Supabase

In the Supabase SQL Editor, run [`supabase-schema.sql`](./supabase-schema.sql)
to create the image table, category index, and private Storage bucket. This is
safe to run more than once.

Set the backend environment variables in `backend/.env` (copy
`.env.example` first):

```properties
SUPABASE_URL=https://your-project-ref.supabase.co
SUPABASE_SERVICE_ROLE_KEY=your-service-role-key
SUPABASE_STORAGE_BUCKET=gallery-images
PHOTO_CATEGORIES=buc\u0103t\u0103rie,camer\u0103 tineret,comode \u0219i noptiere,dressing,diverse,dulap sub scar\u0103,living,paturi de mijloc
FRONTEND_ORIGIN=http://localhost:5173
ADMIN_USERNAME=your-admin-username
ADMIN_PASSWORD=your-long-random-password
```

Find the project URL and service-role key in the Supabase project settings.
Keep the service-role key only in the backend environment; it bypasses row
level security and must never be added to frontend variables or committed.
The backend accepts no database URI and does not connect to MongoDB.
The backend imports `.env` as a Java properties file, so keep non-ASCII
category labels in `PHOTO_CATEGORIES` as `\uXXXX` escapes.

## Image data model

`public.photos` stores `id` (UUID), `category`, `filename`, `content_type`,
`storage_path`, `byte_size`, and `created_at`. The actual image bytes are stored
in a private Supabase Storage bucket, with the corresponding object path in
`storage_path`. The schema restricts content to WebP and size to 200,000 bytes.
RLS is enabled and access is limited to the backend's service-role key.

## Run and test

```powershell
.\mvnw.cmd spring-boot:run
.\mvnw.cmd test
```

The API listens on port 8080 by default. `GET /api/health` works without
Supabase configuration.

## Admin uploads

Open `http://localhost:5173/admin`, sign in with `ADMIN_USERNAME` and
`ADMIN_PASSWORD`, select a category, and choose up to 50 images. JPEG, PNG,
WebP, GIF, AVIF, HEIC, and HEIF files are resized and converted to WebP in the
browser; animated images are saved as still images. Each optimized image must
be no larger than 200 KB. The API enforces the same size limit and checks the
WebP file signature.

The admin API uses HTTP Basic authentication and is disabled until both admin
environment variables are set:

- `GET /api/admin/session` — verify admin credentials.
- `POST /api/admin/photos` — upload multipart form fields `category` and
  repeated `files`.
- `GET /api/stored-photos/{category}/{id}` — serve a private image from Storage.
  Immutable image URLs can be cached by browsers and CDNs for 30 days.

The frontend keeps admin credentials in memory only; use HTTPS in production.
There is one configured admin account and no account creation or registration
flow.

## Deploy to Vercel

Deploy the backend as a separate Vercel project from the same GitHub repository:

1. Set **Root Directory** to `backend` and choose the **Container** preset.
2. Set `SUPABASE_URL`, `SUPABASE_SERVICE_ROLE_KEY`, `SUPABASE_STORAGE_BUCKET`,
   `PHOTO_CATEGORIES`, `ADMIN_USERNAME`, and `ADMIN_PASSWORD`.
3. Set `FRONTEND_ORIGIN` to the deployed frontend's exact origin.

The container listens on port 80. `GET /api/health` can be used to check the
deployment.

## Endpoints

- `GET /api/health` — API health check.
- `GET /api/categories` — configured category names.
- `GET /api/photos?limit=40` — first page of all images.
- `GET /api/photos?category=bucătărie&limit=40` — first page in one category.
- `GET /api/photos?category=bucătărie&limit=40&cursor=...` — next page.

Responses include `photos`, `nextCursor`, `category`, and `pageSize`. Pass
`nextCursor` unchanged to get the next page. Limits are restricted to 1–100 per
request. Responses include HTTP cache headers for browser/CDN caching.
