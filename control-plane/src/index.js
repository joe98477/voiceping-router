const express = require("express");
const cors = require("cors");

const app = express();
const port = Number(process.env.PORT || 4000);

app.use(cors());
app.use(express.json());

const sampleChannels = [
  {
    id: "ch-ops-1",
    name: "Ops North",
    priority: "high",
    monitored: true,
    activeSpeaker: "Unit 12"
  },
  {
    id: "ch-ops-2",
    name: "Ops South",
    priority: "medium",
    monitored: true,
    activeSpeaker: null
  },
  {
    id: "ch-incident-73",
    name: "Incident 73",
    priority: "critical",
    monitored: false,
    activeSpeaker: "Supervisor A"
  },
  {
    id: "ch-interagency",
    name: "Interagency",
    priority: "low",
    monitored: false,
    activeSpeaker: null
  }
];

const sampleRoster = [
  {
    id: "unit-12",
    name: "Unit 12",
    status: "online",
    role: "Field",
    lastChannel: "Ops North"
  },
  {
    id: "unit-18",
    name: "Unit 18",
    status: "talking",
    role: "Field",
    lastChannel: "Incident 73"
  },
  {
    id: "supervisor-a",
    name: "Supervisor A",
    status: "alerting",
    role: "Supervisor",
    lastChannel: "Incident 73"
  },
  {
    id: "dispatcher-3",
    name: "Dispatcher 3",
    status: "online",
    role: "Dispatcher",
    lastChannel: "Ops North"
  }
];

const sampleActivity = [
  {
    id: "act-1",
    timestamp: new Date(Date.now() - 1000 * 60 * 2).toISOString(),
    type: "ptt_start",
    channel: "Incident 73",
    detail: "Supervisor A started talking"
  },
  {
    id: "act-2",
    timestamp: new Date(Date.now() - 1000 * 60 * 5).toISOString(),
    type: "incident_created",
    channel: "Incident 73",
    detail: "Incident channel created for downtown fire"
  },
  {
    id: "act-3",
    timestamp: new Date(Date.now() - 1000 * 60 * 7).toISOString(),
    type: "alert",
    channel: "Ops North",
    detail: "Emergency button pressed by Unit 12"
  }
];

app.get("/health", (req, res) => {
  res.json({ status: "ok", service: "control-plane" });
});

app.get("/api/v1/overview", (req, res) => {
  res.json({
    channels: sampleChannels,
    roster: sampleRoster,
    activity: sampleActivity,
    summary: {
      activeChannels: sampleChannels.length,
      activeIncidents: sampleChannels.filter((channel) => channel.priority === "critical").length,
      onlineUsers: sampleRoster.filter((person) => person.status !== "offline").length
    }
  });
});

app.get("/api/v1/channels", (req, res) => {
  res.json(sampleChannels);
});

app.get("/api/v1/roster", (req, res) => {
  res.json(sampleRoster);
});

app.get("/api/v1/activity", (req, res) => {
  res.json(sampleActivity);
});

app.listen(port, () => {
  console.log(`Control plane API listening on port ${port}`);
});
