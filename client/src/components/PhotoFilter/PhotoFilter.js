import React, { useEffect, useState, useRef } from "react";
import { useSwipeable } from "react-swipeable";
import { getImages } from "../../api";
import "./PhotoFilter.css";

const PhotoFilter = () => {
  const [imageList, setImageList] = useState([]);
  const [filteredList, setFilteredList] = useState([]);
  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [currentIndex, setCurrentIndex] = useState(0);
  const currentIndexRef = useRef(currentIndex);

  const categories = [
    "toate",
    "bucătărie",
    "cameră tineret",
    "comode și noptiere",
    "dressing",
    "dulap sub scară",
    "living",
    "diverse",
    "paturi de mijloc",
  ];

  const fetchData = async () => {
    const responseJson = await getImages();
    setImageList(responseJson.resources);
    setFilteredList(responseJson.resources);
  };

  useEffect(() => {
    fetchData();
  }, []);

  // Keep ref in sync with state
  useEffect(() => {
    currentIndexRef.current = currentIndex;
  }, [currentIndex]);

  const gallery_filter = (itemData) => {
    if (itemData === "toate") {
      setFilteredList(imageList);
    } else {
      const filteredData = imageList.filter((item) =>
        item.asset_folder.includes(itemData)
      );
      setFilteredList(filteredData);
    }
  };

  // Open pop-up at specific index
  const openPopup = (index) => {
    setCurrentIndex(index);
    setIsPopupOpen(true);
  };

  // Close the pop-up
  const closePopup = () => {
    setIsPopupOpen(false);
  };

  // Handle swipe gestures
  const handleSwipe = (direction) => {
    if (
      direction === "LEFT" &&
      currentIndexRef.current < filteredList.length - 1
    ) {
      setCurrentIndex((prevIndex) => prevIndex + 1);
    } else if (direction === "RIGHT" && currentIndexRef.current > 0) {
      setCurrentIndex((prevIndex) => prevIndex - 1);
    }
  };

  const swipeHandlers = useSwipeable({
    onSwipedLeft: () => handleSwipe("LEFT"),
    onSwipedRight: () => handleSwipe("RIGHT"),
    preventDefaultTouchmoveEvent: true,
    trackMouse: true,
  });

  return (
    <div className="App">
      <div className="galleryWrapper">
        <div className="filterItem">
          <select onChange={(e) => gallery_filter(e.target.value)}>
            {categories.map((item, index) => (
              <option value={item} key={index}>
                {item}
              </option>
            ))}
          </select>
        </div>
        <div className="galleryContainer">
          {imageList.length > 0 &&
            filteredList.map((image, index) => (
              <div key={image.asset_id} className="galleryItem">
                <img
                  src={image.url}
                  alt={image.asset_folder}
                  loading="lazy"
                  onClick={() => openPopup(index)}
                />
              </div>
            ))}
        </div>
      </div>

      {/* Pop-up */}
      {isPopupOpen && (
        <div className="popupOverlay" onClick={closePopup}>
          <div
            className="popupContent"
            onClick={(e) => e.stopPropagation()} // Prevent closing when clicking inside
            {...swipeHandlers}
          >
            <button className="closeButton" onClick={closePopup}>
              ×
            </button>
            <div className="photoWrapper">
              <img
                src={filteredList[currentIndex]?.url}
                alt={filteredList[currentIndex]?.asset_folder}
                className="popupImage"
              />
            </div>
            <div className="controls">
              <button
                className="prevButton"
                onClick={() => handleSwipe("RIGHT")}
                disabled={currentIndex === 0}
              >
                ◀
              </button>
              <button
                className="nextButton"
                onClick={() => handleSwipe("LEFT")}
                disabled={currentIndex === filteredList.length - 1}
              >
                ▶
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default PhotoFilter;
