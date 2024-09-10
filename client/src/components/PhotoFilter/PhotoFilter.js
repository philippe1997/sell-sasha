import React, { useEffect, useState } from "react";
import { getImages } from "../../api";

const PhotoFilter = () => {
  const [imageList, setImageList] = useState([]);
  const [filteredList, setFilteredList] = useState([]);

  const categories = [
    "toate",
    "bucătărie",
    "cameră tineret",
    "comode și noptiere",
    "dressing",
    "dulap sub scară",
    "living",
    "mese de machiaj",
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

  return (
    <div className="App">
      <div className="galleryWrapper">
        <div className="filterItem">
          <select onChange={(e) => gallery_filter(e.target.value)}>
            {categories.map((item) => (
              <option value={item}>{item}</option>
            ))}
          </select>
        </div>
        <div className="galleryContainer">
          {imageList.length > 0 &&
            filteredList.map((image) => (
              <div key={image.asset_id} className="galleryItem">
                <img
                  src={image.url}
                  alt={image.public_id}
                  onClick={() => window.open(`${image.url}`)}
                />
              </div>
            ))}
        </div>
      </div>
    </div>
  );
};

export default PhotoFilter;
