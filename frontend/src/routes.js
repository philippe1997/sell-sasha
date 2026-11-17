const BASE_PATH = import.meta.env.BASE_URL.replace(/\/+$/, "");

export const HOME_PATH = `${BASE_PATH}/`;
export const ADMIN_PATH = `${BASE_PATH}/admin`;

export function isAdminPath(pathname) {
  const normalizedPath = pathname.replace(/\/+$/, "") || "/";
  return normalizedPath === ADMIN_PATH || normalizedPath === "/admin";
}
