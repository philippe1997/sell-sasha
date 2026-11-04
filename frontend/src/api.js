const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080")
  .replace(/\/+$/, "");

async function request(path, signal) {
  const response = await fetch(`${API_BASE_URL}${path}`, { signal });
  let data;
  try {
    data = await response.json();
  } catch {
    if (!response.ok) {
      throw new Error(`Request failed (${response.status})`);
    }
    throw new Error("The photo service returned an invalid response.");
  }

  if (!response.ok) {
    throw new Error(data?.message || `Request failed (${response.status})`);
  }

  return data;
}

export async function getPhotos({ category, cursor, limit, signal }) {
  const params = new URLSearchParams({ limit: String(limit) });
  if (category) params.set("category", category);
  if (cursor) params.set("cursor", cursor);
  const page = await request(`/api/photos?${params}`, signal);
  if (
    !page ||
    !Array.isArray(page.photos) ||
    !Number.isInteger(page.totalCount) ||
    page.totalCount < 0 ||
    (page.nextCursor !== null && typeof page.nextCursor !== "string")
  ) {
    throw new Error("The photo service returned an invalid photo page.");
  }
  return page;
}
