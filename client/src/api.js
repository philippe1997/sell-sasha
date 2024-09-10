const API_URL = process.env.REACT_APP_API_URL;

export const getImages = async () => {
  const response = await fetch("http://localhost:7000/photos");
  const responseJson = response.json();

  return responseJson;
};
