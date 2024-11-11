const API_URL = process.env.REACT_APP_API_URL;

export const getImages = async () => {
  const response = await fetch(
    "https://comfy-sunburst-673d6b.netlify.app/.netlify/functions/api/photos"
  );
  const responseJson = await response.json();

  return responseJson;
};
