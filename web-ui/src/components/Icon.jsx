import React from "react";

const Icon = ({ path, size = 20, title }) => (
  <svg viewBox="0 0 24 24" width={size} height={size} aria-hidden={title ? undefined : "true"}>
    {title ? <title>{title}</title> : null}
    <path d={path} fill="currentColor" />
  </svg>
);

export default Icon;
