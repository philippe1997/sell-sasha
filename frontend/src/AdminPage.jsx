import { useState } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { CATEGORIES } from "./categories.js";
import { uploadPhotos, verifyAdmin } from "./api.js";
import { optimizeImage } from "./optimizeImage.js";
import { ADMIN_PATH, HOME_PATH } from "./routes.js";

const MAX_UPLOAD_FILES = 40;
const CATEGORY_REQUIRED_ERROR =
  "Selecteaza o categorie inainte de a incarca imagini.";

function formatCategory(category) {
  return category.charAt(0).toLocaleUpperCase("ro-RO") + category.slice(1);
}

export default function AdminPage() {
  const queryClient = useQueryClient();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [credentials, setCredentials] = useState(null);
  const [category, setCategory] = useState("");
  const [files, setFiles] = useState([]);
  const [isChecking, setIsChecking] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [uploadProgress, setUploadProgress] = useState("");
  const [error, setError] = useState("");
  const [success, setSuccess] = useState("");

  async function handleLogin(event) {
    event.preventDefault();
    setError("");
    setIsChecking(true);
    try {
      const submittedCredentials = { username, password };
      await verifyAdmin(submittedCredentials);
      setCredentials(submittedCredentials);
      setPassword("");
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsChecking(false);
    }
  }

  async function handleUpload(event) {
    event.preventDefault();
    const form = event.currentTarget;
    if (!category) {
      setError(CATEGORY_REQUIRED_ERROR);
      return;
    }
    if (files.length === 0) {
      setError("Selecteaza cel putin o imagine de incarcat.");
      return;
    }
    if (files.length > MAX_UPLOAD_FILES) {
      setError(`Poți încărca cel mult ${MAX_UPLOAD_FILES} imagini simultan.`);
      return;
    }

    setError("");
    setSuccess("");
    setIsUploading(true);
    try {
      const optimizedFiles = [];
      for (const [index, file] of files.entries()) {
        setUploadProgress(
          `Optimizare imaginii ${index + 1} din ${files.length}…`,
        );
        optimizedFiles.push(await optimizeImage(file));
      }
      setUploadProgress("Incarcare imagini optimizate…");
      const uploaded = await uploadPhotos({
        category,
        files: optimizedFiles,
        credentials,
      });
      await queryClient.invalidateQueries({ queryKey: ["photos"] });
      setSuccess(
        `${uploaded.length} imagin${uploaded.length === 1 ? "e" : "i"} incarcat${uploaded.length === 1 ? "a" : "e"}.`,
      );
      setFiles([]);
      setCategory("");
      form.reset();
    } catch (requestError) {
      setError(requestError.message);
    } finally {
      setIsUploading(false);
      setUploadProgress("");
    }
  }

  return (
    <main className="admin-page">
      <header className="admin-header">
        <a className="admin-brand" href={HOME_PATH}>
          Sell Sasha Admin
        </a>
        <nav className="site-nav" aria-label="Main navigation"></nav>
      </header>

      <section className="admin-panel">
        {!credentials ? (
          <>
            <form className="admin-form" onSubmit={handleLogin}>
              <label>
                Utilizator
                <input
                  autoComplete="username"
                  required
                  value={username}
                  onChange={(event) => setUsername(event.target.value)}
                />
              </label>
              <label>
                Parola
                <input
                  type="password"
                  autoComplete="current-password"
                  required
                  value={password}
                  onChange={(event) => setPassword(event.target.value)}
                />
              </label>
              <button type="submit" disabled={isChecking}>
                {isChecking ? "In curs de verificare…" : "Loghează-te"}
              </button>
            </form>
          </>
        ) : (
          <>
            <div className="admin-session">
              <span>
                <b>Logat ca:</b> {credentials.username}
              </span>
              <button
                className="admin-secondary-button"
                type="button"
                onClick={() => {
                  setCredentials(null);
                  setFiles([]);
                  setSuccess("");
                  setError("");
                }}
              >
                Deloghează-te
              </button>
            </div>
            <form className="admin-form" onSubmit={handleUpload}>
              <label>
                Categorie
                <select
                  value={category}
                  required
                  onInvalid={() => setError(CATEGORY_REQUIRED_ERROR)}
                  onChange={(event) => {
                    setCategory(event.target.value);
                    setError((currentError) =>
                      currentError === CATEGORY_REQUIRED_ERROR
                        ? ""
                        : currentError,
                    );
                  }}
                >
                  <option value="">Selecteaza o categorie...</option>
                  {CATEGORIES.map((item) => (
                    <option key={item} value={item}>
                      {formatCategory(item)}
                    </option>
                  ))}
                </select>
              </label>
              <label>
                Imagini
                <input
                  accept="image/jpeg,image/png,image/webp,image/gif,image/avif,image/heic,image/heif,.heic,.heif"
                  multiple
                  required
                  type="file"
                  onChange={(event) =>
                    setFiles(Array.from(event.target.files ?? []))
                  }
                />
              </label>
              <p className="admin-hint">
                JPEG, PNG, WebP, GIF, AVIF, HEIC or HEIF · Pana la{" "}
                {MAX_UPLOAD_FILES} fisiere.
              </p>
              {files.length > 0 && (
                <ul className="admin-file-list" aria-label="Selected images">
                  {files.map((file, index) => (
                    <li key={`${file.name}-${index}`}>
                      <span>{file.name}</span>
                      <span>{(file.size / 1000).toFixed(0)} KB source</span>
                    </li>
                  ))}
                </ul>
              )}
              <button
                type="submit"
                disabled={isUploading || files.length === 0}
              >
                {isUploading
                  ? "Se încarcă…"
                  : `Încarcă ${files.length} imagini`}
              </button>
            </form>
            {uploadProgress && (
              <p className="admin-hint" role="status" aria-live="polite">
                {uploadProgress}
              </p>
            )}
          </>
        )}

        {error && (
          <p className="admin-feedback admin-feedback-error" role="alert">
            {error}
          </p>
        )}
        {success && (
          <p className="admin-feedback admin-feedback-success" role="status">
            {success}
          </p>
        )}
      </section>
    </main>
  );
}
