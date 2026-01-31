import React, { useEffect, useId, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import { mdiInformationOutline } from "../icons.js";

const InfoPopover = ({ title, details }) => {
  const [open, setOpen] = useState(false);
  const popoverId = useId();
  const containerRef = useRef(null);

  useEffect(() => {
    if (!open) {
      return undefined;
    }
    const handleOutsideClick = (event) => {
      if (containerRef.current && !containerRef.current.contains(event.target)) {
        setOpen(false);
      }
    };
    document.addEventListener("mousedown", handleOutsideClick);
    return () => document.removeEventListener("mousedown", handleOutsideClick);
  }, [open]);

  return (
    <div className="dispatch-card__info" ref={containerRef}>
      <button
        type="button"
        className="dispatch-card__info-btn"
        aria-label={`View ${title} details`}
        aria-haspopup="dialog"
        aria-expanded={open}
        aria-controls={popoverId}
        onClick={() => setOpen((prev) => !prev)}
      >
        <Icon path={mdiInformationOutline} size={14} />
      </button>
      {open ? (
        <div className="dispatch-card__popover" id={popoverId} role="dialog" aria-label={`${title} details`}>
          <div className="dispatch-card__popover-title">{title}</div>
          <dl className="dispatch-card__popover-list">
            {details.map((detail) => (
              <div key={detail.label} className="dispatch-card__popover-row">
                <dt>{detail.label}</dt>
                <dd>{detail.value}</dd>
              </div>
            ))}
          </dl>
        </div>
      ) : null}
    </div>
  );
};

export default InfoPopover;
