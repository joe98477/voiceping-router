const apiBase = document.body.dataset.apiBase || "http://localhost:4000";
const channelsEl = document.getElementById("channels");
const rosterEl = document.getElementById("roster");
const activityEl = document.getElementById("activity");
const summaryChannels = document.getElementById("summary-channels");
const summaryIncidents = document.getElementById("summary-incidents");
const summaryOnline = document.getElementById("summary-online");
const activeChannel = document.getElementById("active-channel");
const clockEl = document.getElementById("clock");

const defaultData = {
  summary: {
    activeChannels: 4,
    activeIncidents: 1,
    onlineUsers: 12
  },
  channels: [
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
    }
  ],
  roster: [
    {
      id: "unit-12",
      name: "Unit 12",
      status: "online",
      role: "Field",
      lastChannel: "Ops North"
    },
    {
      id: "supervisor-a",
      name: "Supervisor A",
      status: "alerting",
      role: "Supervisor",
      lastChannel: "Incident 73"
    }
  ],
  activity: [
    {
      id: "act-1",
      timestamp: new Date().toISOString(),
      type: "alert",
      channel: "Ops North",
      detail: "Emergency tone detected on Ops North"
    }
  ]
};

const badgeForPriority = (priority) => {
  if (priority === "critical") {
    return "channel-card__badge channel-card__badge--critical";
  }
  if (priority === "high") {
    return "channel-card__badge channel-card__badge--high";
  }
  if (priority === "low") {
    return "channel-card__badge channel-card__badge--low";
  }
  return "channel-card__badge";
};

const formatTime = (iso) => new Date(iso).toLocaleTimeString();

const render = (data) => {
  summaryChannels.textContent = data.summary.activeChannels;
  summaryIncidents.textContent = data.summary.activeIncidents;
  summaryOnline.textContent = data.summary.onlineUsers;

  channelsEl.innerHTML = data.channels
    .map(
      (channel) => `
      <article class="channel-card">
        <div class="channel-card__title">${channel.name}</div>
        <div class="channel-card__meta">
          <span>${channel.monitored ? "Monitoring" : "Idle"}</span>
          <span class="${badgeForPriority(channel.priority)}">${channel.priority}</span>
        </div>
        <div class="channel-card__meta">
          <span>Speaker</span>
          <strong>${channel.activeSpeaker || "—"}</strong>
        </div>
      </article>
    `
    )
    .join("");

  rosterEl.innerHTML = data.roster
    .map(
      (person) => `
      <article class="roster-card">
        <div class="roster-card__name">${person.name}</div>
        <div class="roster-card__meta">
          <span>${person.role}</span>
          <span>${person.status}</span>
        </div>
        <div class="roster-card__meta">
          <span>Last channel</span>
          <strong>${person.lastChannel}</strong>
        </div>
      </article>
    `
    )
    .join("");

  activityEl.innerHTML = data.activity
    .map(
      (item) => `
      <article class="activity-item">
        <div class="activity-item__header">
          <span>${item.channel}</span>
          <span>${formatTime(item.timestamp)}</span>
        </div>
        <div class="activity-item__detail">${item.detail}</div>
      </article>
    `
    )
    .join("");

  if (data.channels.length > 0) {
    activeChannel.textContent = data.channels[0].name;
  }
};

const tickClock = () => {
  clockEl.textContent = new Date().toLocaleTimeString();
};

const init = async () => {
  tickClock();
  setInterval(tickClock, 1000);

  try {
    const response = await fetch(`${apiBase}/api/v1/overview`);
    if (!response.ok) {
      throw new Error("Failed to load overview");
    }
    const data = await response.json();
    render(data);
  } catch (error) {
    render(defaultData);
  }
};

init();
