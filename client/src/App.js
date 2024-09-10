import React from "react";
import { BrowserRouter as Router, Route, Routes } from "react-router-dom";
import Header from "./components/Header/Header";
import Footer from "./components/Footer/Footer";
import PhotoFilter from "./components/PhotoFilter/PhotoFilter";
import HomePage from "./pages/HomePage/HomePage";
import "./App.css";

const App = () => {
  return (
    <Router>
      <div className="App">
        <Header />
        <HomePage />
        <PhotoFilter />
        <Footer />
      </div>
    </Router>
  );
};

export default App;
