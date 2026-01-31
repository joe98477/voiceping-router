import React, { useEffect, useState } from "react";
import { Navigate, Route, Routes } from "react-router-dom";
import { apiGet, apiGetStatus, apiPost } from "./api.js";
import Login from "./pages/Login.jsx";
import Events from "./pages/Events.jsx";
import Console from "./pages/Console.jsx";
import SystemSettings from "./pages/SystemSettings.jsx";
import ResetPassword from "./pages/ResetPassword.jsx";
import FirstRun from "./pages/FirstRun.jsx";
import Startup from "./pages/Startup.jsx";

const App = () => {
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(false);
  const [systemReady, setSystemReady] = useState(false);
  const [startupStatus, setStartupStatus] = useState(null);
  const [startupError, setStartupError] = useState("");
  const showStartupDetails =
    import.meta.env.VITE_STARTUP_STATUS === "true" || import.meta.env.VITE_STARTUP_STATUS === "1";

  const refreshUser = async () => {
    setLoading(true);
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
    if (systemReady) {
      return;
    }
    let cancelled = false;
    const poll = async () => {
      if (cancelled) {
        return;
      }
      const result = await apiGetStatus("/api/ready");
      if (cancelled) {
        return;
      }
      if (result.data && result.data.services) {
        setStartupStatus(result.data);
        setStartupError("");
        if (result.data.ready) {
          setSystemReady(true);
        }
      } else if (!result.ok) {
        setStartupError(result.error || "Unable to reach control plane");
      }
    };
    poll();
    const interval = setInterval(poll, 2500);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, [systemReady]);

  useEffect(() => {
    if (systemReady) {
      refreshUser();
    }
  }, [systemReady]);

  const handleLogout = async () => {
    await apiPost("/api/auth/logout");
    setUser(null);
  };

  if (!systemReady) {
    return (
      <Startup
        status={startupStatus}
        error={startupError}
        showDetails={showStartupDetails}
        onRetry={() => {
          setStartupError("");
          setStartupStatus(null);
          setSystemReady(false);
        }}
      />
    );
  }

  if (loading) {
    return <div className="screen screen--center">Loading...</div>;
  }

  const needsSetup = user && (user.mustChangePassword || !user.displayName || !user.email);

  return (
    <Routes>
      <Route
        path="/login"
        element={
          user ? (
            <Navigate to={needsSetup ? "/first-run" : "/events"} replace />
          ) : (
            <Login onLogin={refreshUser} />
          )
        }
      />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route
        path="/first-run"
        element={
          user ? (
            needsSetup ? (
              <FirstRun onComplete={refreshUser} user={user} />
            ) : (
              <Navigate to="/events" replace />
            )
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/events"
        element={
          user ? (
            needsSetup ? (
              <Navigate to="/first-run" replace />
            ) : (
              <Events user={user} onLogout={handleLogout} />
            )
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/event/:eventId/dispatch"
        element={
          user ? (
            needsSetup ? (
              <Navigate to="/first-run" replace />
            ) : (
              <Console user={user} onLogout={handleLogout} />
            )
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/event/:eventId/admin"
        element={
          user ? (
            needsSetup ? (
              <Navigate to="/first-run" replace />
            ) : (
              <Console user={user} onLogout={handleLogout} />
            )
          ) : (
            <Navigate to="/login" replace />
          )
        }
      />
      <Route
        path="/admin/settings"
        element={
          user && user.globalRole === "ADMIN" ? (
            needsSetup ? (
              <Navigate to="/first-run" replace />
            ) : (
              <SystemSettings onLogout={handleLogout} />
            )
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
