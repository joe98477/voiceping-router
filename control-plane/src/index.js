const express = require("express");
const cors = require("cors");
const cookieParser = require("cookie-parser");
const session = require("express-session");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const nodemailer = require("nodemailer");
const crypto = require("crypto");
const { PrismaClient } = require("@prisma/client");
const { createClient } = require("redis");
const connectRedis = require("connect-redis");
const RedisStore = connectRedis.default || connectRedis;

const app = express();
const port = Number(process.env.PORT || 4000);
const prisma = new PrismaClient();

const ROUTER_JWT_SECRET = process.env.ROUTER_JWT_SECRET || process.env.SECRET_KEY || "voiceping-router-secret";
const SESSION_SECRET = process.env.SESSION_SECRET || "voiceping-session-secret";
const LEGACY_JOIN_ENABLED = process.env.LEGACY_JOIN_ENABLED === "true";

const REDIS_HOST = process.env.REDIS_HOST || "127.0.0.1";
const REDIS_PORT = Number(process.env.REDIS_PORT || 6379);
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || undefined;
const REDIS_URL = process.env.REDIS_URL || undefined;

const SMTP_HOST = process.env.SMTP_HOST;
const SMTP_PORT = Number(process.env.SMTP_PORT || 587);
const SMTP_USER = process.env.SMTP_USER;
const SMTP_PASS = process.env.SMTP_PASS;
const SMTP_FROM = process.env.SMTP_FROM || "no-reply@voiceping.local";

const DEFAULT_LIMITS = {
  maxUsers: 1000,
  maxTeams: 100,
  maxChannels: 200,
  maxChannelsPerTeam: 50,
  maxDispatch: 20
};

const redisClient = createClient({
  url: REDIS_URL,
  socket: REDIS_URL ? undefined : { host: REDIS_HOST, port: REDIS_PORT },
  password: REDIS_PASSWORD
});
const redisPublisher = createClient({
  url: REDIS_URL,
  socket: REDIS_URL ? undefined : { host: REDIS_HOST, port: REDIS_PORT },
  password: REDIS_PASSWORD
});

redisClient.connect().catch(() => null);
redisPublisher.connect().catch(() => null);

app.use(cors({ origin: true, credentials: true }));
app.use(express.json());
app.use(cookieParser());

app.use(
  session({
    store: new RedisStore({ client: redisClient }),
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: { httpOnly: true, sameSite: "lax" }
  })
);

const transporter = SMTP_HOST
  ? nodemailer.createTransport({
      host: SMTP_HOST,
      port: SMTP_PORT,
      secure: SMTP_PORT === 465,
      auth: SMTP_USER ? { user: SMTP_USER, pass: SMTP_PASS } : undefined
    })
  : null;

const sendEmail = async (to, subject, text) => {
  if (!transporter) {
    return { skipped: true };
  }
  return transporter.sendMail({ from: SMTP_FROM, to, subject, text });
};

const bootstrapAdmin = async () => {
  const email = process.env.BOOTSTRAP_ADMIN_EMAIL;
  const password = process.env.BOOTSTRAP_ADMIN_PASSWORD;
  if (!email || !password) {
    return;
  }
  const existing = await prisma.user.findUnique({ where: { email } });
  if (existing) {
    return;
  }
  const passwordHash = await bcrypt.hash(password, 12);
  await prisma.user.create({
    data: { email, passwordHash, globalRole: "ADMIN" }
  });
};

const requireAuth = async (req, res, next) => {
  if (!req.session || !req.session.userId) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  const user = await prisma.user.findUnique({ where: { id: req.session.userId } });
  if (!user) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  req.user = user;
  return next();
};

const requireAdmin = async (req, res, next) => {
  if (!req.user || req.user.globalRole !== "ADMIN") {
    return res.status(403).json({ error: "Forbidden" });
  }
  return next();
};

const requireDispatchOrAdmin = async (req, res, next) => {
  if (req.user && req.user.globalRole === "ADMIN") {
    return next();
  }
  const eventId = req.params.eventId || req.body.eventId;
  if (!eventId) {
    return res.status(400).json({ error: "Missing eventId" });
  }
  const membership = await prisma.eventMembership.findUnique({
    where: { userId_eventId: { userId: req.user.id, eventId } }
  });
  if (!membership || membership.role !== "DISPATCH") {
    return res.status(403).json({ error: "Forbidden" });
  }
  return next();
};

const buildLimits = (limits) => ({
  ...DEFAULT_LIMITS,
  ...(limits || {})
});

const audit = async (actorId, eventId, action, payload) => {
  await prisma.auditLog.create({
    data: { actorId: actorId || null, eventId: eventId || null, action, payload }
  });
};

const syncUserChannelsToRedis = async (userId, channelIds) => {
  const key = `u.${userId}.g`;
  const previousChannels = await redisClient.sMembers(key);
  const nextChannels = channelIds || [];
  const previousSet = new Set(previousChannels);
  const nextSet = new Set(nextChannels);

  for (const channelId of previousSet) {
    if (!nextSet.has(channelId)) {
      await redisClient.sRem(`g.${channelId}.u`, userId);
    }
  }
  for (const channelId of nextSet) {
    await redisClient.sAdd(`g.${channelId}.u`, userId);
  }
  await redisClient.del(key);
  if (nextChannels.length > 0) {
    await redisClient.sAdd(key, nextChannels);
  }
};

const publishMembershipUpdate = async (eventId, userId, channelIds) => {
  const payload = JSON.stringify({
    eventId,
    userId,
    channelIds,
    action: "set_user_channels"
  });
  await redisPublisher.publish("vp:membership_updates", payload);
};

const recomputeUserMembership = async (userId, eventId) => {
  const activeChannels = await prisma.channelMembership.findMany({
    where: {
      userId,
      status: "ACTIVE",
      channel: { eventId }
    },
    select: { channelId: true }
  });
  const channelIds = activeChannels.map((entry) => entry.channelId);
  await syncUserChannelsToRedis(userId, channelIds);
  await publishMembershipUpdate(eventId, userId, channelIds);
};

app.get("/health", (req, res) => {
  res.json({ status: "ok", service: "control-plane" });
});

app.post("/api/auth/login", async (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: "Missing credentials" });
  }
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    return res.status(401).json({ error: "Invalid credentials" });
  }
  const valid = await bcrypt.compare(password, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: "Invalid credentials" });
  }
  req.session.userId = user.id;
  return res.json({ id: user.id, email: user.email, displayName: user.displayName, globalRole: user.globalRole });
});

app.post("/api/auth/logout", requireAuth, (req, res) => {
  req.session.destroy(() => {
    res.json({ ok: true });
  });
});

app.get("/api/auth/me", requireAuth, (req, res) => {
  res.json({ id: req.user.id, email: req.user.email, displayName: req.user.displayName, globalRole: req.user.globalRole });
});

app.post("/api/auth/forgot-password", async (req, res) => {
  const { email } = req.body || {};
  if (!email) {
    return res.status(400).json({ error: "Missing email" });
  }
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    return res.json({ ok: true });
  }
  const token = crypto.randomBytes(24).toString("hex");
  const expiresAt = new Date(Date.now() + 1000 * 60 * 30);
  await prisma.passwordReset.create({
    data: { userId: user.id, token, expiresAt }
  });
  const resetUrl = `${process.env.WEB_BASE_URL || "http://localhost:8080"}/reset-password?token=${token}`;
  await sendEmail(email, "VoicePing password reset", `Reset your password: ${resetUrl}`);
  return res.json({ ok: true });
});

app.post("/api/auth/reset-password", async (req, res) => {
  const { token, password } = req.body || {};
  if (!token || !password) {
    return res.status(400).json({ error: "Missing data" });
  }
  const reset = await prisma.passwordReset.findUnique({ where: { token } });
  if (!reset || reset.usedAt || reset.expiresAt < new Date()) {
    return res.status(400).json({ error: "Invalid token" });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  await prisma.user.update({ where: { id: reset.userId }, data: { passwordHash } });
  await prisma.passwordReset.update({ where: { id: reset.id }, data: { usedAt: new Date() } });
  return res.json({ ok: true });
});

app.post("/api/admin/users", requireAuth, requireAdmin, async (req, res) => {
  const { email, password, displayName, globalRole } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: "Missing email/password" });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  const user = await prisma.user.create({
    data: {
      email,
      passwordHash,
      displayName: displayName || null,
      globalRole: globalRole === "ADMIN" ? "ADMIN" : "NONE"
    }
  });
  await audit(req.user.id, null, "user_created", { userId: user.id });
  res.json({ id: user.id, email: user.email, globalRole: user.globalRole });
});

app.post("/api/events", requireAuth, requireAdmin, async (req, res) => {
  const { name, requiresApproval, limits } = req.body || {};
  if (!name) {
    return res.status(400).json({ error: "Missing name" });
  }
  const event = await prisma.event.create({
    data: {
      name,
      requiresApproval: !!requiresApproval,
      limits: buildLimits(limits),
      createdById: req.user.id
    }
  });
  await audit(req.user.id, event.id, "event_created", { eventId: event.id });
  res.json(event);
});

app.patch("/api/events/:eventId", requireAuth, requireAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { name, requiresApproval, limits } = req.body || {};
  const event = await prisma.event.update({
    where: { id: eventId },
    data: {
      name: name || undefined,
      requiresApproval: typeof requiresApproval === "boolean" ? requiresApproval : undefined,
      limits: limits ? buildLimits(limits) : undefined
    }
  });
  await audit(req.user.id, eventId, "event_updated", { eventId });
  res.json(event);
});

app.get("/api/events", requireAuth, async (req, res) => {
  if (req.user.globalRole === "ADMIN") {
    const events = await prisma.event.findMany({ orderBy: { createdAt: "desc" } });
    return res.json(events);
  }
  const memberships = await prisma.eventMembership.findMany({
    where: { userId: req.user.id },
    include: { event: true }
  });
  return res.json(memberships.map((m) => m.event));
});

app.post("/api/events/:eventId/teams", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { name } = req.body || {};
  if (!name) {
    return res.status(400).json({ error: "Missing name" });
  }
  const event = await prisma.event.findUnique({ where: { id: eventId } });
  const teamCount = await prisma.team.count({ where: { eventId } });
  if (teamCount >= event.limits.maxTeams) {
    return res.status(400).json({ error: "Team limit reached" });
  }
  const team = await prisma.team.create({ data: { eventId, name } });
  await audit(req.user.id, eventId, "team_created", { teamId: team.id });
  res.json(team);
});

app.post("/api/teams/:teamId/channels", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { teamId } = req.params;
  const { name } = req.body || {};
  if (!name) {
    return res.status(400).json({ error: "Missing name" });
  }
  const team = await prisma.team.findUnique({ where: { id: teamId } });
  if (!team) {
    return res.status(404).json({ error: "Team not found" });
  }
  const event = await prisma.event.findUnique({ where: { id: team.eventId } });
  const teamChannelCount = await prisma.channel.count({ where: { teamId } });
  const eventChannelCount = await prisma.channel.count({ where: { eventId: team.eventId } });
  if (teamChannelCount >= event.limits.maxChannelsPerTeam || eventChannelCount >= event.limits.maxChannels) {
    return res.status(400).json({ error: "Channel limit reached" });
  }
  const channel = await prisma.channel.create({
    data: { eventId: team.eventId, teamId, name, type: "TEAM" }
  });
  await audit(req.user.id, team.eventId, "channel_created", { channelId: channel.id });
  res.json(channel);
});

app.post("/api/events/:eventId/channels", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { name } = req.body || {};
  if (!name) {
    return res.status(400).json({ error: "Missing name" });
  }
  const event = await prisma.event.findUnique({ where: { id: eventId } });
  const eventChannelCount = await prisma.channel.count({ where: { eventId } });
  if (eventChannelCount >= event.limits.maxChannels) {
    return res.status(400).json({ error: "Channel limit reached" });
  }
  const channel = await prisma.channel.create({
    data: { eventId, name, type: "EVENT_ADMIN" }
  });
  await audit(req.user.id, eventId, "admin_channel_created", { channelId: channel.id });
  res.json(channel);
});

app.post("/api/events/:eventId/users", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { userId, role, teamIds, channelIds } = req.body || {};
  if (!userId || !role) {
    return res.status(400).json({ error: "Missing userId/role" });
  }
  const event = await prisma.event.findUnique({ where: { id: eventId } });
  const membershipCount = await prisma.eventMembership.count({ where: { eventId } });
  if (membershipCount >= event.limits.maxUsers) {
    return res.status(400).json({ error: "User limit reached" });
  }
  if (role === "DISPATCH") {
    const dispatchCount = await prisma.eventMembership.count({
      where: { eventId, role: "DISPATCH" }
    });
    if (dispatchCount >= event.limits.maxDispatch) {
      return res.status(400).json({ error: "Dispatch limit reached" });
    }
  }
  const existingMembership = await prisma.eventMembership.findUnique({
    where: { userId_eventId: { userId, eventId } }
  });
  const status = existingMembership
    ? existingMembership.status
    : event.requiresApproval
    ? "PENDING"
    : "ACTIVE";
  await prisma.eventMembership.upsert({
    where: { userId_eventId: { userId, eventId } },
    create: { userId, eventId, role, status, assignedBy: req.user.id },
    update: { role, status }
  });
  if (Array.isArray(teamIds)) {
    for (const teamId of teamIds) {
      await prisma.teamMembership.upsert({
        where: { userId_teamId: { userId, teamId } },
        create: { userId, teamId, status },
        update: { status }
      });
    }
  }
  if (Array.isArray(channelIds)) {
    for (const channelId of channelIds) {
      await prisma.channelMembership.upsert({
        where: { userId_channelId: { userId, channelId } },
        create: { userId, channelId, status },
        update: { status }
      });
    }
  }
  await audit(req.user.id, eventId, "event_user_assigned", { userId });
  if (status === "ACTIVE") {
    await recomputeUserMembership(userId, eventId);
  }
  res.json({ ok: true, status });
});

app.patch("/api/events/:eventId/users/:userId/approve", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId, userId } = req.params;
  await prisma.eventMembership.update({
    where: { userId_eventId: { userId, eventId } },
    data: { status: "ACTIVE", approvedBy: req.user.id }
  });
  await prisma.teamMembership.updateMany({
    where: { userId, team: { eventId } },
    data: { status: "ACTIVE" }
  });
  await prisma.channelMembership.updateMany({
    where: { userId, channel: { eventId } },
    data: { status: "ACTIVE" }
  });
  await recomputeUserMembership(userId, eventId);
  await audit(req.user.id, eventId, "event_user_approved", { userId });
  res.json({ ok: true });
});

app.patch("/api/events/:eventId/users/:userId/channels", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId, userId } = req.params;
  const { channelIds } = req.body || {};
  if (!Array.isArray(channelIds)) {
    return res.status(400).json({ error: "Missing channelIds" });
  }
  const membership = await prisma.eventMembership.findUnique({
    where: { userId_eventId: { userId, eventId } }
  });
  const status = membership && membership.status === "ACTIVE" ? "ACTIVE" : "PENDING";
  await prisma.channelMembership.deleteMany({
    where: { userId, channel: { eventId } }
  });
  for (const channelId of channelIds) {
    await prisma.channelMembership.create({
      data: { userId, channelId, status }
    });
  }
  if (status === "ACTIVE") {
    await recomputeUserMembership(userId, eventId);
  }
  await audit(req.user.id, eventId, "user_channels_updated", { userId, channelIds });
  res.json({ ok: true });
});

app.patch("/api/events/:eventId/users/:userId/teams", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId, userId } = req.params;
  const { teamIds } = req.body || {};
  if (!Array.isArray(teamIds)) {
    return res.status(400).json({ error: "Missing teamIds" });
  }
  const membership = await prisma.eventMembership.findUnique({
    where: { userId_eventId: { userId, eventId } }
  });
  const status = membership && membership.status === "ACTIVE" ? "ACTIVE" : "PENDING";
  await prisma.teamMembership.deleteMany({
    where: { userId, team: { eventId } }
  });
  for (const teamId of teamIds) {
    await prisma.teamMembership.create({
      data: { userId, teamId, status }
    });
  }
  await audit(req.user.id, eventId, "user_teams_updated", { userId, teamIds });
  res.json({ ok: true });
});

app.post("/api/events/:eventId/invites", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { teamId, channelIds, expiresInMinutes, maxUses, email } = req.body || {};
  const token = crypto.randomBytes(16).toString("hex");
  const expiresAt = new Date(Date.now() + 1000 * 60 * (expiresInMinutes || 15));
  const invite = await prisma.invite.create({
    data: {
      eventId,
      teamId: teamId || null,
      defaultChannels: channelIds || [],
      expiresAt,
      maxUses: maxUses || 0,
      createdById: req.user.id,
      token
    }
  });
  await audit(req.user.id, eventId, "invite_created", { inviteId: invite.id });
  if (email) {
    const joinUrl = `${process.env.WEB_BASE_URL || "http://localhost:8080"}/login?invite=${token}`;
    await sendEmail(email, "VoicePing event invite", `Join the event: ${joinUrl}`);
  }
  res.json({ token: invite.token, expiresAt: invite.expiresAt });
});

app.post("/api/invites/:token/join", requireAuth, async (req, res) => {
  const { token } = req.params;
  const invite = await prisma.invite.findUnique({ where: { token } });
  if (!invite || invite.expiresAt < new Date()) {
    return res.status(400).json({ error: "Invite expired" });
  }
  if (invite.maxUses > 0 && invite.usedCount >= invite.maxUses) {
    return res.status(400).json({ error: "Invite used up" });
  }
  const event = await prisma.event.findUnique({ where: { id: invite.eventId } });
  const status = event.requiresApproval ? "PENDING" : "ACTIVE";
  await prisma.eventMembership.upsert({
    where: { userId_eventId: { userId: req.user.id, eventId: invite.eventId } },
    create: { userId: req.user.id, eventId: invite.eventId, role: "USER", status },
    update: { status }
  });
  if (invite.teamId) {
    await prisma.teamMembership.upsert({
      where: { userId_teamId: { userId: req.user.id, teamId: invite.teamId } },
      create: { userId: req.user.id, teamId: invite.teamId, status },
      update: { status }
    });
  }
  if (Array.isArray(invite.defaultChannels)) {
    for (const channelId of invite.defaultChannels) {
      await prisma.channelMembership.upsert({
        where: { userId_channelId: { userId: req.user.id, channelId } },
        create: { userId: req.user.id, channelId, status },
        update: { status }
      });
    }
  }
  await prisma.invite.update({
    where: { id: invite.id },
    data: { usedCount: invite.usedCount + 1 }
  });
  await audit(req.user.id, invite.eventId, "invite_joined", { inviteId: invite.id });
  if (status === "ACTIVE") {
    await recomputeUserMembership(req.user.id, invite.eventId);
  }
  res.json({ ok: true, status });
});

app.get("/api/events/:eventId/overview", requireAuth, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const event = await prisma.event.findUnique({ where: { id: eventId } });
  const teams = await prisma.team.findMany({ where: { eventId }, orderBy: { sortOrder: "asc" } });
  const channels = await prisma.channel.findMany({ where: { eventId }, orderBy: { sortOrder: "asc" } });
  const memberships = await prisma.eventMembership.findMany({
    where: { eventId },
    include: { user: true }
  });
  const pending = memberships.filter((m) => m.status === "PENDING");
  res.json({
    event,
    teams,
    channels,
    roster: memberships.map((m) => ({
      id: m.user.id,
      email: m.user.email,
      displayName: m.user.displayName,
      role: m.role,
      status: m.status
    })),
    pendingCount: pending.length
  });
});

app.post("/api/router/token", requireAuth, async (req, res) => {
  const { eventId } = req.body || {};
  if (!eventId) {
    return res.status(400).json({ error: "Missing eventId" });
  }
  const membership = await prisma.eventMembership.findUnique({
    where: { userId_eventId: { userId: req.user.id, eventId } }
  });
  if (!membership || membership.status !== "ACTIVE") {
    return res.status(403).json({ error: "Not active in event" });
  }
  const channelMemberships = await prisma.channelMembership.findMany({
    where: { userId: req.user.id, status: "ACTIVE", channel: { eventId } },
    select: { channelId: true }
  });
  const channelIds = channelMemberships.map((entry) => entry.channelId);
  const token = jwt.sign(
    {
      userId: req.user.id,
      eventId,
      role: membership.role,
      channelIds
    },
    ROUTER_JWT_SECRET,
    { expiresIn: "12h" }
  );
  res.json({ token });
});

app.listen(port, async () => {
  await bootstrapAdmin();
  console.log(`Control plane API listening on port ${port}`);
});
