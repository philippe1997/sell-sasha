# Sell Sasha photo gallery

The full-stack gallery is split into two independent projects:

- [backend/](./backend/README.md) — Java 17 and Spring Boot API. It stores image
  metadata in Supabase Postgres and image bytes in a private Supabase Storage
  bucket.
- [frontend/](./frontend/README.md) — React and Vite gallery. It fetches the
  next 40 images as you scroll and virtualizes the grid so off-screen cards
  aren't kept in the DOM.

The admin upload page is available at `/sell-sasha/admin` (the frontend's
configured base path plus `/admin`). Configure `ADMIN_USERNAME`,
`ADMIN_PASSWORD`, `SUPABASE_URL`, and `SUPABASE_SERVICE_ROLE_KEY` in the
backend environment before using it. Create the Supabase table and private
bucket by running [`backend/supabase-schema.sql`](./backend/supabase-schema.sql)
in the project's SQL Editor.

## Requirements

- Java 17+
- Node.js 20.19+ or 22.12+
- A Supabase project

## Start locally (Windows PowerShell)

Create `backend/.env` from `.env.example`, set Supabase and admin credentials,
then start Spring Boot:

```powershell
cd backend
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

Each photo has a category in the Supabase `public.photos` table. Its WebP bytes
are stored in the private `gallery-images` bucket. Set `PHOTO_CATEGORIES` to
the category labels shown in the UI. Selecting a category calls
`GET /api/photos?category=bucătărie&limit=40`; the backend filters Supabase
metadata and streams private image data from Storage.

Supabase service-role credentials stay on the backend and must not be added to
frontend environment variables. See the project READMEs for backend and
frontend configuration, endpoints, and production build commands.
