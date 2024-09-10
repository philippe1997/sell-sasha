import React from "react";
import { Link } from "react-router-dom";
import "./Header.css";

const Header = () => {
  return (
    <header className="header">
      <div className="logo">
        <Link to="/">Sell Sasha</Link>
      </div>
    </header>
  );
};

export default Header;
