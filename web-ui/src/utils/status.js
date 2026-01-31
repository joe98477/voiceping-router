export const statusToKey = (status) => {
  if (!status) {
    return "offline";
  }
  return status.toLowerCase();
};

export const statusLabel = (status) => {
  switch (status) {
    case "ACTIVE":
      return "Active";
    case "STANDBY":
      return "Standby";
    case "OFFLINE":
      return "Offline";
    default:
      return "Offline";
  }
};
