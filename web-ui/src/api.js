const rawBase = import.meta.env.VITE_API_BASE;
const API_BASE =
  rawBase !== undefined ? rawBase : import.meta.env.MODE === "development" ? "http://localhost:4000" : "";

export const apiFetch = async (path, options = {}) => {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 10000);
  let response;
  try {
    response = await fetch(`${API_BASE}${path}`, {
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...(options.headers || {})
      },
      signal: controller.signal,
      ...options
    });
  } catch (err) {
    if (err && err.name === "AbortError") {
      const timeoutErr = new Error("Request timed out");
      timeoutErr.status = 408;
      throw timeoutErr;
    }
    throw err;
  } finally {
    clearTimeout(timeout);
  }
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
