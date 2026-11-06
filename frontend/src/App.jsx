import { useEffect, useMemo, useRef, useState } from "react";
import { useInfiniteQuery } from "@tanstack/react-query";
import { VirtuosoGrid } from "react-virtuoso";
import { getPhotos } from "./api.js";
import { CATEGORIES } from "./categories.js";

function formatCategory(category) {
  return category
    ? category.charAt(0).toLocaleUpperCase("ro-RO") + category.slice(1)
    : category;
}

function PhotoCard({ photo, onOpen }) {
  const [failed, setFailed] = useState(false);

  return (
    <button
      className="photo-card"
      type="button"
      onClick={() => onOpen(photo)}
      aria-label={`Deschide imaginea ${photo.publicId}`}
    >
      {failed ? (
        <span
          className="photo-fallback"
          role="img"
          aria-label="Imagine indisponibilă"
        >
          <span>Imagine indisponibilă</span>
        </span>
      ) : (
        <img
          src={photo.thumbnailUrl}
          alt={photo.publicId}
          loading="eager"
          decoding="async"
          onError={() => setFailed(true)}
        />
      )}
      <span className="photo-caption">{photo.folder || photo.publicId}</span>
    </button>
  );
}

function PhotoLightbox({
  photo,
  photoIndex,
  photoCount,
  totalPhotoCount,
  canGoNext,
  isLoadingNext,
  navigationError,
  previousPhoto,
  nextPhoto,
  onClose,
  onPrevious,
  onNext,
}) {
  const touchStart = useRef(null);
  const waitingForNextPage = isLoadingNext && photoIndex === photoCount - 1;

  useEffect(() => {
    const adjacentImages = [previousPhoto, nextPhoto].filter(Boolean);
    adjacentImages.forEach((adjacentPhoto) => {
      const image = new Image();
      image.decoding = "async";
      image.fetchPriority = "low";
      image.src = adjacentPhoto.displayUrl;
    });
  }, [previousPhoto, nextPhoto]);

  useEffect(() => {
    function handleKeyDown(event) {
      if (event.key === "Escape") onClose();
      if (event.key === "ArrowLeft" && photoIndex > 0) onPrevious();
      if (event.key === "ArrowRight" && canGoNext && !waitingForNextPage) {
        onNext();
      }
    }

    window.addEventListener("keydown", handleKeyDown);
    return () => window.removeEventListener("keydown", handleKeyDown);
  }, [canGoNext, onClose, onNext, onPrevious, photoIndex, waitingForNextPage]);

  if (!photo) return null;

  return (
    <div
      className="lightbox"
      role="presentation"
      onTouchStart={(event) => {
        const touch = event.touches[0];
        touchStart.current = { x: touch.clientX, y: touch.clientY };
      }}
      onTouchEnd={(event) => {
        if (!touchStart.current) return;
        const touch = event.changedTouches[0];
        const deltaX = touch.clientX - touchStart.current.x;
        const deltaY = touch.clientY - touchStart.current.y;
        touchStart.current = null;
        if (Math.abs(deltaX) < 50 || Math.abs(deltaX) <= Math.abs(deltaY))
          return;
        if (deltaX < 0 && canGoNext && !waitingForNextPage) onNext();
        if (deltaX > 0 && photoIndex > 0) onPrevious();
      }}
    >
      <div
        className="lightbox-content"
        role="dialog"
        aria-modal="true"
        aria-label="Vizualizator de imagini"
        onMouseDown={(event) => {
          if (event.target === event.currentTarget) onClose();
        }}
      >
        <button
          className="lightbox-close"
          type="button"
          onClick={onClose}
          aria-label="Închide imaginea"
        >
          ×
        </button>
        <button
          className="lightbox-navigation lightbox-previous"
          type="button"
          onClick={onPrevious}
          disabled={photoIndex === 0}
          aria-label="Imaginea anterioară"
        >
          <span className="lightbox-chevron lightbox-chevron-previous" aria-hidden="true" />
        </button>
        <img
          src={photo.displayUrl}
          alt={photo.publicId}
          decoding="async"
          fetchPriority="high"
        />
        <button
          className="lightbox-navigation lightbox-next"
          type="button"
          onClick={onNext}
          disabled={!canGoNext || waitingForNextPage}
          aria-label={
            waitingForNextPage
              ? "Se încarcă următoarea imagine"
              : "Imaginea următoare"
          }
        >
          {waitingForNextPage ? (
            "…"
          ) : (
            <span className="lightbox-chevron lightbox-chevron-next" aria-hidden="true" />
          )}
        </button>
        <div className="lightbox-caption">
          <p>
            Imaginea {photoIndex + 1} din {totalPhotoCount}
          </p>
        </div>
        {navigationError && (
          <p className="lightbox-error" role="alert">
            Nu s-au putut încărca următoarele imagini: {navigationError}
          </p>
        )}
      </div>
    </div>
  );
}

export default function App() {
  const [category, setCategory] = useState("");
  const [selectedPhotoId, setSelectedPhotoId] = useState(null);
  const [hasReachedEnd, setHasReachedEnd] = useState(false);
  const galleryRef = useRef(null);

  const photosQuery = useInfiniteQuery({
    queryKey: ["photos", category],
    queryFn: ({ pageParam, signal }) =>
      getPhotos({
        category,
        cursor: pageParam.cursor,
        limit: pageParam.limit,
        signal,
      }),
    initialPageParam: { cursor: undefined, limit: 10 },
    getNextPageParam: (lastPage) =>
      lastPage.nextCursor
        ? { cursor: lastPage.nextCursor, limit: 20 }
        : undefined,
    staleTime: 5 * 60 * 1000,
  });

  const photos = useMemo(
    () => photosQuery.data?.pages.flatMap((page) => page.photos) ?? [],
    [photosQuery.data],
  );
  const totalPhotoCount = photosQuery.data?.pages[0]?.totalCount;
  const selectedPhotoIndex = photos.findIndex(
    (photo) => photo.id === selectedPhotoId,
  );
  const selectedPhoto =
    selectedPhotoIndex >= 0 ? photos[selectedPhotoIndex] : null;
  const isLightboxOpen = selectedPhoto !== null;

  useEffect(() => {
    if (!hasReachedEnd || photosQuery.isFetching) return;

    setHasReachedEnd(false);
    if (photosQuery.hasNextPage) {
      photosQuery.fetchNextPage({ cancelRefetch: false });
    }
  }, [
    hasReachedEnd,
    photosQuery.fetchNextPage,
    photosQuery.hasNextPage,
    photosQuery.isFetching,
  ]);

  useEffect(() => {
    if (!isLightboxOpen) return;

    const previousOverflow = document.body.style.overflow;
    document.body.style.overflow = "hidden";

    return () => {
      document.body.style.overflow = previousOverflow;
    };
  }, [isLightboxOpen]);

  useEffect(() => {
    if (
      selectedPhotoId &&
      selectedPhotoIndex >= photos.length - 3 &&
      photosQuery.hasNextPage &&
      !photosQuery.isFetchingNextPage
    ) {
      photosQuery.fetchNextPage();
    }
  }, [
    photos.length,
    photosQuery.fetchNextPage,
    photosQuery.hasNextPage,
    photosQuery.isFetchingNextPage,
    selectedPhotoId,
    selectedPhotoIndex,
  ]);

  async function navigatePhoto(direction) {
    const targetIndex = selectedPhotoIndex + direction;
    if (targetIndex >= 0 && targetIndex < photos.length) {
      setSelectedPhotoId(photos[targetIndex].id);
      return;
    }

    if (
      direction > 0 &&
      targetIndex === photos.length &&
      photosQuery.hasNextPage &&
      !photosQuery.isFetchingNextPage
    ) {
      const result = await photosQuery.fetchNextPage();
      const nextPhoto = result.data?.pages
        .flatMap((page) => page.photos)
        .at(targetIndex);
      if (nextPhoto) setSelectedPhotoId(nextPhoto.id);
    }
  }

  return (
    <>
      <main className="page">
        <header className="site-header">
          <p className="eyebrow">Sell Sasha</p>
          <h1>Mobilier creat pentru a te simți acasă.</h1>
          <p className="intro">
            Descoperă colecția noastră, realizată cu atenție la detalii și
            cerințele clientului.
          </p>
        </header>

        <section
          ref={galleryRef}
          className="gallery-section"
          id="gallery"
          aria-label="Galerie de mobilier"
        >
          <div className="gallery-heading">
            <div>
              <p className="eyebrow">Colecția</p>
              <h2>{formatCategory(category) || "Toate proiectele"}</h2>
            </div>
            <span className="photo-count">
              {totalPhotoCount !== undefined
                ? `${totalPhotoCount} ${totalPhotoCount === 1 ? "imagine în total" : "imagini în total"}`
                : ""}
            </span>
          </div>

          <div className="category-filter">
            <label htmlFor="category-select">Filtrează după: </label>
            <select
              id="category-select"
              value={category}
              onChange={(event) => {
                setSelectedPhotoId(null);
                setHasReachedEnd(false);
                galleryRef.current?.scrollIntoView({ block: "start" });
                setCategory(event.target.value);
              }}
            >
              <option value="">Toate proiectele</option>
              {CATEGORIES.map((item) => (
                <option key={item} value={item}>
                  {formatCategory(item)}
                </option>
              ))}
            </select>
          </div>

          {photosQuery.isPending && (
            <p className="status-message">Se încarcă imaginile…</p>
          )}

          {photosQuery.isError && photos.length === 0 && (
            <div className="error-message" role="alert">
              <p>
                Nu s-au putut încărca imaginile: {photosQuery.error.message}
              </p>
              <button type="button" onClick={() => photosQuery.refetch()}>
                Încearcă din nou
              </button>
            </div>
          )}

          {!photosQuery.isPending &&
            !photosQuery.isError &&
            photos.length === 0 && (
              <p className="status-message">
                Nu s-au găsit imagini. Verifică dacă imaginile din Cloudinary au
                eticheta categoriei selectate.
              </p>
            )}

          {photos.length > 0 && (
            <VirtuosoGrid
              key={category}
              className="photo-grid-scroller"
              useWindowScroll
              data={photos}
              overscan={500}
              increaseViewportBy={{ top: 400, bottom: 1000 }}
              endReached={() => setHasReachedEnd(true)}
              computeItemKey={(_index, photo) => photo.id}
              itemContent={(_index, photo) => (
                <PhotoCard
                  photo={photo}
                  onOpen={(selected) => setSelectedPhotoId(selected.id)}
                />
              )}
              listClassName="photo-grid"
              itemClassName="photo-grid-item"
            />
          )}

          {photosQuery.isFetchingNextPage && (
            <p className="status-message" aria-live="polite">
              Se încarcă mai multe imagini…
            </p>
          )}

          {photosQuery.isError && photos.length > 0 && (
            <div className="error-message" role="alert">
              <p>
                Nu s-a putut încărca următoarea pagină:{" "}
                {photosQuery.error.message}
              </p>
              <button type="button" onClick={() => photosQuery.fetchNextPage()}>
                Încearcă din nou
              </button>
            </div>
          )}

          {!photosQuery.hasNextPage && photos.length > 0 && (
            <p className="end-message">Ai ajuns la sfârșitul galeriei.</p>
          )}
        </section>

        <PhotoLightbox
          photo={selectedPhoto}
          photoIndex={selectedPhotoIndex}
          photoCount={photos.length}
          totalPhotoCount={totalPhotoCount ?? photos.length}
          canGoNext={
            selectedPhotoIndex < photos.length - 1 || photosQuery.hasNextPage
          }
          isLoadingNext={photosQuery.isFetchingNextPage}
          navigationError={
            photosQuery.isError ? photosQuery.error.message : null
          }
          previousPhoto={photos[selectedPhotoIndex - 1]}
          nextPhoto={photos[selectedPhotoIndex + 1]}
          onClose={() => setSelectedPhotoId(null)}
          onPrevious={() => navigatePhoto(-1)}
          onNext={() => navigatePhoto(1)}
        />
      </main>
      <footer className="site-footer" role="contentinfo">
        <div className="site-footer-content">
          <p className="site-footer-item">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="14"
              height="14"
              fill="currentColor"
              viewBox="0 0 16 16"
              aria-hidden="true"
            >
              <path d="M3 14s-1 0-1-1 1-4 6-4 6 3 6 4-1 1-1 1zm5-6a3 3 0 1 0 0-6 3 3 0 0 0 0 6" />
            </svg>
            <span>Alexandru Popescu</span>
          </p>
          <p className="site-footer-item">© 2022 SELL SASHA SRL</p>
          <p className="site-footer-item">
            <svg
              xmlns="http://www.w3.org/2000/svg"
              width="14"
              height="14"
              fill="currentColor"
              viewBox="0 0 16 16"
              aria-hidden="true"
            >
              <path d="M3.654 1.328a.678.678 0 0 0-1.015-.063L1.605 2.3c-.483.484-.661 1.169-.45 1.77a17.6 17.6 0 0 0 4.168 6.608 17.6 17.6 0 0 0 6.608 4.168c.601.211 1.286.033 1.77-.45l1.034-1.034a.678.678 0 0 0-.063-1.015l-2.307-1.794a.68.68 0 0 0-.58-.122l-2.19.547a1.75 1.75 0 0 1-1.657-.459L5.482 8.062a1.75 1.75 0 0 1-.46-1.657l.548-2.19a.68.68 0 0 0-.122-.58zM1.884.511a1.745 1.745 0 0 1 2.612.163L6.29 2.98c.329.423.445.974.315 1.494l-.547 2.19a.68.68 0 0 0 .178.643l2.457 2.457a.68.68 0 0 0 .644.178l2.189-.547a1.75 1.75 0 0 1 1.494.315l2.306 1.794c.829.645.905 1.87.163 2.611l-1.034 1.034c-.74.74-1.846 1.065-2.877.702a18.6 18.6 0 0 1-7.01-4.42 18.6 18.6 0 0 1-4.42-7.009c-.362-1.03-.037-2.137.703-2.877z" />
            </svg>
            <a href="tel:+40775389807">+40 775 389 807</a>
          </p>
        </div>
      </footer>
    </>
  );
}
