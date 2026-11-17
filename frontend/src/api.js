const API_BASE_URL = (import.meta.env.VITE_API_BASE_URL || "http://localhost:8080")
  .replace(/\/+$/, "");

async function request(path, signal, options = {}) {
  const response = await fetch(`${API_BASE_URL}${path}`, { ...options, signal });
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
  return {
    ...page,
    photos: page.photos.map((photo) => ({
      ...photo,
      thumbnailUrl: new URL(photo.thumbnailUrl, API_BASE_URL).href,
      displayUrl: new URL(photo.displayUrl, API_BASE_URL).href,
    })),
  };
}

function basicAuthorization({ username, password }) {
  const encodedCredentials = new TextEncoder().encode(`${username}:${password}`);
  const binaryCredentials = Array.from(encodedCredentials, (byte) =>
    String.fromCharCode(byte),
  ).join("");
  return `Basic ${btoa(binaryCredentials)}`;
}

export async function verifyAdmin(credentials) {
  await request("/api/admin/session", undefined, {
    headers: { Authorization: basicAuthorization(credentials) },
  });
}

export async function uploadPhotos({ category, files, credentials }) {
  const formData = new FormData();
  formData.set("category", category);
  files.forEach((file) => formData.append("files", file));
  return request("/api/admin/photos", undefined, {
    method: "POST",
    headers: { Authorization: basicAuthorization(credentials) },
    body: formData,
  });
}
