import React, { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { apiGet, apiPost } from "./api.js";
import Login from "./pages/Login.jsx";
import Events from "./pages/Events.jsx";
import Dispatch from "./pages/Dispatch.jsx";
import Admin from "./pages/Admin.jsx";
import SystemSettings from "./pages/SystemSettings.jsx";
import ResetPassword from "./pages/ResetPassword.jsx";

const App = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  const refreshUser = async () => {
    try {
      const me = await apiGet("/api/auth/me");
      setUser(me);
    } catch (err) {
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    refreshUser();
  }, []);

  const handleLogout = async () => {
    await apiPost("/api/auth/logout");
    setUser(null);
  };

  if (loading) {
    return <div className="screen screen--center">Loading…</div>;
  }

  return (
    <Routes>
      <Route path="/login" element={<Login onLogin={refreshUser} />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route
        path="/events"
        element={
          user ? <Events user={user} onLogout={handleLogout} /> : <Navigate to="/login" replace />
        }
      />
      <Route
        path="/event/:eventId/dispatch"
        element={user ? <Dispatch user={user} onLogout={handleLogout} /> : <Navigate to="/login" replace />}
      />
      <Route
        path="/event/:eventId/admin"
        element={user ? <Admin user={user} onLogout={handleLogout} /> : <Navigate to="/login" replace />}
      />
      <Route
        path="/admin/settings"
        element={
          user && user.globalRole === "ADMIN" ? (
            <SystemSettings onLogout={handleLogout} />
          ) : (
            <Navigate to="/events" replace />
          )
        }
      />
      <Route path="*" element={<Navigate to={user ? "/events" : "/login"} replace />} />
    </Routes>
  );
};

export default App;
