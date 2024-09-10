import React from "react";
import { Link } from "react-router-dom";
import "./ProductCard.css";

const ProductCard = ({ product }) => {
  return (
    <div className="product-card">
      <img src={product.image} alt={product.name} />
      <h2>{product.name}</h2>
      <Link to={`/products/${product.id}`} className="details-button">
        Vezi detalii
      </Link>
    </div>
  );
};

export default ProductCard;
