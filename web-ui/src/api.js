const API_BASE = import.meta.env.VITE_API_BASE || "http://localhost:4000";

export const apiFetch = async (path, options = {}) => {
  const response = await fetch(`${API_BASE}${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers || {})
    },
    ...options
  });
  if (!response.ok) {
    let error = "Request failed";
    try {
      const data = await response.json();
      error = data.error || error;
    } catch (err) {
      error = response.statusText || error;
    }
    const errObj = new Error(error);
    errObj.status = response.status;
    throw errObj;
  }
  if (response.status === 204) {
    return null;
  }
  return response.json();
};

export const apiGet = (path) => apiFetch(path, { method: "GET" });
export const apiPost = (path, body) =>
  apiFetch(path, { method: "POST", body: JSON.stringify(body || {}) });
export const apiPatch = (path, body) =>
  apiFetch(path, { method: "PATCH", body: JSON.stringify(body || {}) });
