import React from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import App from "./App.jsx";
import "./styles.css";
import "./theme/connectvoice.css";

const root = document.getElementById("root");
if (root) {
  document.body.classList.add("theme-connectvoice");
}
createRoot(root).render(
  <BrowserRouter>
    <App />
  </BrowserRouter>
);
