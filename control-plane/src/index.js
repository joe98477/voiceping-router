const express = require("express");
const cors = require("cors");
const cookieParser = require("cookie-parser");
const session = require("express-session");
const bcrypt = require("bcryptjs");
const jwt = require("jsonwebtoken");
const nodemailer = require("nodemailer");
const crypto = require("crypto");
const net = require("net");
const { PrismaClient } = require("@prisma/client");
const { createClient } = require("redis");
const connectRedis = require("connect-redis");
const RedisStore = connectRedis.default || connectRedis;
const commonPasswords = require("./passwords/common.json");

const app = express();
const port = Number(process.env.PORT || 4000);
const prisma = new PrismaClient();

const isProd = process.env.NODE_ENV === "production";
const ROUTER_JWT_SECRET =
  process.env.ROUTER_JWT_SECRET || process.env.SECRET_KEY || (isProd ? null : "awesomevoiceping");
const SESSION_SECRET = process.env.SESSION_SECRET || "voiceping-session-secret";
const LEGACY_JOIN_ENABLED = process.env.LEGACY_JOIN_ENABLED === "true";

const REDIS_HOST = process.env.REDIS_HOST || "127.0.0.1";
const REDIS_PORT = Number(process.env.REDIS_PORT || 6379);
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || undefined;
const REDIS_URL = process.env.REDIS_URL || undefined;
const ROUTER_HOST = process.env.ROUTER_HOST || "127.0.0.1";
const ROUTER_PORT = Number(process.env.ROUTER_PORT || 3000);
const ROUTER_STATUS_TIMEOUT_MS = Number(process.env.ROUTER_STATUS_TIMEOUT_MS || 500);
const MAXIMUM_IDLE_DURATION = Number(process.env.MAXIMUM_IDLE_DURATION || 3000);
const TEST_SEED_ENABLED = process.env.TEST_SEED_ENABLED === "true";
const TEST_SEED_EVENT_NAME = process.env.TEST_SEED_EVENT_NAME || "VoicePing Test Event";
const TEST_SEED_TEAM_NAME = process.env.TEST_SEED_TEAM_NAME || "Test Team";
const TEST_SEED_CHANNEL_NAME = process.env.TEST_SEED_CHANNEL_NAME || "Test Channel";

const SMTP_HOST = process.env.SMTP_HOST;
const SMTP_PORT = Number(process.env.SMTP_PORT || 587);
const SMTP_USER = process.env.SMTP_USER;
const SMTP_PASS = process.env.SMTP_PASS;
const SMTP_FROM = process.env.SMTP_FROM || "no-reply@voiceping.local";
const SETTINGS_ID = 1;
const trustProxy = process.env.TRUST_PROXY === "true";
const sessionCookieSecure =
  process.env.SESSION_COOKIE_SECURE === "true" ||
  (isProd && process.env.SESSION_COOKIE_SECURE !== "false");

const DEFAULT_LIMITS = {
  maxUsers: 1000,
  maxTeams: 100,
  maxChannels: 200,
  maxChannelsPerTeam: 50,
  maxDispatch: 20
};

const ACTIVE_TRAFFIC_WINDOW_MS = 60 * 1000;
const ACTIVITY_SAMPLE_SIZE = Number(process.env.ACTIVITY_SAMPLE_SIZE || 50);

const parseMessageMeta = (messageId) => {
  if (!messageId || typeof messageId !== "string") {
    return null;
  }
  const parts = messageId.split("_");
  if (parts.length < 5) {
    return null;
  }
  const fromId = parts[3];
  const timestampRaw = parts[4].split(".")[0];
  const timestamp = Number(timestampRaw);
  if (!fromId || Number.isNaN(timestamp)) {
    return null;
  }
  return { fromId, timestamp };
};

const deriveStatus = (hasTraffic, hasConnection) => {
  if (hasTraffic) {
    return "ACTIVE";
  }
  if (hasConnection) {
    return "STANDBY";
  }
  return "OFFLINE";
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

if (isProd && !ROUTER_JWT_SECRET) {
  throw new Error("ROUTER_JWT_SECRET or SECRET_KEY must be set in production.");
}

const readiness = {
  database: { ok: false, error: null },
  redis: { ok: false, error: null },
  redisPublisher: { ok: false, error: null }
};

const setReadyState = (key, ok, error) => {
  readiness[key].ok = ok;
  readiness[key].error = error ? String(error.message || error) : null;
};

const isSystemReady = () =>
  readiness.database.ok && readiness.redis.ok && readiness.redisPublisher.ok;

const attachRedisEvents = (client, key) => {
  client.on("ready", () => {
    setReadyState(key, true, null);
  });
  client.on("end", () => {
    setReadyState(key, false, "Redis connection closed");
  });
  client.on("error", (err) => {
    setReadyState(key, false, err);
    console.error(`[${key}] Redis error`, err);
  });
};

attachRedisEvents(redisClient, "redis");
attachRedisEvents(redisPublisher, "redisPublisher");

const startRedis = async () => {
  try {
    await redisClient.connect();
  } catch (err) {
    setReadyState("redis", false, err);
    console.error("Redis client failed to connect", err);
  }
  try {
    await redisPublisher.connect();
  } catch (err) {
    setReadyState("redisPublisher", false, err);
    console.error("Redis publisher failed to connect", err);
  }
};

const startDatabase = async () => {
  try {
    await prisma.$connect();
    setReadyState("database", true, null);
  } catch (err) {
    setReadyState("database", false, err);
    console.error("Database connection failed", err);
    setTimeout(startDatabase, 5000);
  }
};

const checkRouterReachable = () =>
  new Promise((resolve) => {
    const started = Date.now();
    const socket = net.createConnection({ host: ROUTER_HOST, port: ROUTER_PORT });
    let settled = false;
    const finish = (ok, error) => {
      if (settled) {
        return;
      }
      settled = true;
      socket.destroy();
      resolve({
        ok,
        host: ROUTER_HOST,
        port: ROUTER_PORT,
        latencyMs: Date.now() - started,
        error: error ? String(error.message || error) : null
      });
    };
    socket.setTimeout(ROUTER_STATUS_TIMEOUT_MS);
    socket.on("connect", () => finish(true, null));
    socket.on("timeout", () => finish(false, new Error("Timeout")));
    socket.on("error", (err) => finish(false, err));
  });

const buildStatusPayload = async () => {
  const router = await checkRouterReachable();
  return {
    ready: isSystemReady(),
    services: {
      database: { ok: readiness.database.ok, error: readiness.database.error },
      redis: { ok: readiness.redis.ok, error: readiness.redis.error },
      redisPublisher: { ok: readiness.redisPublisher.ok, error: readiness.redisPublisher.error },
      router
    },
    timestamp: new Date().toISOString()
  };
};

app.use(cors({ origin: true, credentials: true }));
app.use(express.json());
app.use(cookieParser());
if (trustProxy) {
  app.set("trust proxy", 1);
}

app.get("/health", (req, res) => {
  res.json({ status: "ok", service: "control-plane" });
});

app.get("/api/ready", async (req, res) => {
  const payload = await buildStatusPayload();
  res.status(payload.ready ? 200 : 503).json(payload);
});

app.use((req, res, next) => {
  if (req.path === "/health" || req.path === "/api/ready") {
    return next();
  }
  if (!isSystemReady()) {
    return res.status(503).json({ error: "Service starting", ready: false });
  }
  return next();
});

app.use(
  session({
    store: new RedisStore({ client: redisClient }),
    secret: SESSION_SECRET,
    resave: false,
    saveUninitialized: false,
    cookie: { httpOnly: true, sameSite: "lax", secure: sessionCookieSecure }
  })
);

const SETTINGS_KEY = crypto.createHash("sha256").update(SESSION_SECRET).digest();

const normalizeEmpty = (value) => {
  if (value === undefined || value === null) {
    return null;
  }
  if (typeof value === "string" && value.trim() === "") {
    return null;
  }
  return value;
};

const encryptSecret = (plain) => {
  if (!plain) {
    return null;
  }
  const iv = crypto.randomBytes(12);
  const cipher = crypto.createCipheriv("aes-256-gcm", SETTINGS_KEY, iv);
  const encrypted = Buffer.concat([cipher.update(String(plain), "utf8"), cipher.final()]);
  const tag = cipher.getAuthTag();
  return `${iv.toString("base64")}:${tag.toString("base64")}:${encrypted.toString("base64")}`;
};

const decryptSecret = (payload) => {
  if (!payload) {
    return null;
  }
  try {
    const [ivB64, tagB64, dataB64] = payload.split(":");
    if (!ivB64 || !tagB64 || !dataB64) {
      return null;
    }
    const iv = Buffer.from(ivB64, "base64");
    const tag = Buffer.from(tagB64, "base64");
    const data = Buffer.from(dataB64, "base64");
    const decipher = crypto.createDecipheriv("aes-256-gcm", SETTINGS_KEY, iv);
    decipher.setAuthTag(tag);
    const decrypted = Buffer.concat([decipher.update(data), decipher.final()]);
    return decrypted.toString("utf8");
  } catch (err) {
    return null;
  }
};

const buildTransporter = (config) => {
  if (!config || !config.host) {
    return null;
  }
  try {
    return nodemailer.createTransport({
      host: config.host,
      port: config.port,
      secure: config.port === 465,
      auth: config.user ? { user: config.user, pass: config.pass || undefined } : undefined
    });
  } catch (err) {
    return null;
  }
};

const ensureSystemSettings = async () => {
  const existing = await prisma.systemSettings.findUnique({ where: { id: SETTINGS_ID } });
  if (existing) {
    return existing;
  }
  return prisma.systemSettings.create({
    data: {
      id: SETTINGS_ID,
      smtpHost: normalizeEmpty(SMTP_HOST),
      smtpPort: Number(SMTP_PORT || 587),
      smtpUser: normalizeEmpty(SMTP_USER),
      smtpPassEncrypted: encryptSecret(normalizeEmpty(SMTP_PASS)),
      smtpFrom: normalizeEmpty(SMTP_FROM || "no-reply@voiceping.local")
    }
  });
};

const resolveSmtpConfig = async () => {
  const settings = await ensureSystemSettings();
  return {
    host: normalizeEmpty(settings.smtpHost),
    port: Number(settings.smtpPort || 587),
    user: normalizeEmpty(settings.smtpUser),
    pass: decryptSecret(settings.smtpPassEncrypted),
    from: normalizeEmpty(settings.smtpFrom) || "no-reply@voiceping.local"
  };
};

let smtpTransporter = null;
let smtpConfigCache = null;

const getSmtpTransporter = async () => {
  const config = await resolveSmtpConfig();
  const current = smtpConfigCache;
  const changed =
    !current ||
    current.host !== config.host ||
    current.port !== config.port ||
    current.user !== config.user ||
    current.pass !== config.pass ||
    current.from !== config.from;
  if (changed) {
    smtpTransporter = buildTransporter(config);
    smtpConfigCache = config;
  }
  return { transporter: smtpTransporter, config };
};

const sendEmail = async (to, subject, text) => {
  const { transporter, config } = await getSmtpTransporter();
  if (!transporter) {
    return { skipped: true };
  }
  return transporter.sendMail({ from: config.from, to, subject, text });
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
    data: { email, passwordHash, globalRole: "ADMIN", mustChangePassword: true }
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
  if (user.disabledAt) {
    req.session.destroy(() => {
      res.status(403).json({ error: "Account disabled" });
    });
    return;
  }
  req.user = user;
  return next();
};

const requireProfileComplete = async (req, res, next) => {
  if (!req.user) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  if (req.user.mustChangePassword || !req.user.displayName || !req.user.email) {
    return res.status(412).json({ error: "Profile setup required" });
  }
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

const findTestSeedEvent = async () =>
  prisma.event.findFirst({
    where: { name: TEST_SEED_EVENT_NAME },
    include: { teams: true, channels: true }
  });

const ensureTestSeed = async () => {
  const existing = await findTestSeedEvent();
  if (existing) {
    return existing;
  }
  const event = await prisma.event.create({
    data: {
      name: TEST_SEED_EVENT_NAME,
      requiresApproval: false,
      limits: buildLimits()
    }
  });
  const team = await prisma.team.create({
    data: { eventId: event.id, name: TEST_SEED_TEAM_NAME }
  });
  await prisma.channel.create({
    data: { eventId: event.id, teamId: team.id, name: TEST_SEED_CHANNEL_NAME, type: "TEAM" }
  });
  return findTestSeedEvent();
};

const removeTestSeed = async () => {
  const existing = await findTestSeedEvent();
  if (!existing) {
    return null;
  }
  await prisma.event.delete({ where: { id: existing.id } });
  return existing;
};

const validatePasswordStrength = (password) => {
  if (!password || typeof password !== "string") {
    return { ok: false, error: "Password required" };
  }
  if (password.length < 12) {
    return { ok: false, error: "Password must be at least 12 characters" };
  }
  const normalized = password.trim().toLowerCase();
  if (commonPasswords.includes(normalized)) {
    return { ok: false, error: "Password is too common" };
  }
  return { ok: true };
};

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

app.post("/api/auth/login", async (req, res) => {
  const { email, password } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: "Missing credentials" });
  }
  const user = await prisma.user.findUnique({ where: { email } });
  if (!user) {
    return res.status(401).json({ error: "Invalid credentials" });
  }
  if (user.disabledAt) {
    return res.status(403).json({ error: "Account disabled" });
  }
  const valid = await bcrypt.compare(password, user.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: "Invalid credentials" });
  }
  req.session.userId = user.id;
  const updated = await prisma.user.update({
    where: { id: user.id },
    data: { lastLoginAt: new Date() }
  });
  return res.json({
    id: updated.id,
    email: updated.email,
    displayName: updated.displayName,
    globalRole: updated.globalRole,
    mustChangePassword: updated.mustChangePassword
  });
});

app.post("/api/auth/logout", requireAuth, (req, res) => {
  req.session.destroy(() => {
    res.json({ ok: true });
  });
});

app.get("/api/auth/me", requireAuth, (req, res) => {
  res.json({
    id: req.user.id,
    email: req.user.email,
    displayName: req.user.displayName,
    globalRole: req.user.globalRole,
    mustChangePassword: req.user.mustChangePassword
  });
});

app.post("/api/auth/first-run", requireAuth, async (req, res) => {
  const { displayName, password, email } = req.body || {};
  if (!req.user.mustChangePassword && req.user.displayName && req.user.email) {
    return res.status(400).json({ error: "Profile already complete" });
  }
  if (!displayName || typeof displayName !== "string") {
    return res.status(400).json({ error: "Display name required" });
  }
  const trimmedEmail = typeof email === "string" ? email.trim() : "";
  if (!trimmedEmail && !req.user.email) {
    return res.status(400).json({ error: "Email required" });
  }
  if (trimmedEmail) {
    const existing = await prisma.user.findUnique({ where: { email: trimmedEmail } });
    if (existing && existing.id !== req.user.id) {
      return res.status(400).json({ error: "Email already in use" });
    }
  }
  const validation = validatePasswordStrength(password);
  if (!validation.ok) {
    return res.status(400).json({ error: validation.error });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  const data = {
    displayName: displayName.trim(),
    passwordHash,
    mustChangePassword: false,
    passwordUpdatedAt: new Date()
  };
  if (trimmedEmail && trimmedEmail !== req.user.email) {
    data.email = trimmedEmail;
  }
  const updated = await prisma.user.update({
    where: { id: req.user.id },
    data
  });
  res.json({
    id: updated.id,
    email: updated.email,
    displayName: updated.displayName,
    globalRole: updated.globalRole,
    mustChangePassword: updated.mustChangePassword
  });
});

app.post("/api/auth/change-password", requireAuth, requireProfileComplete, async (req, res) => {
  const { currentPassword, newPassword } = req.body || {};
  if (!currentPassword || !newPassword) {
    return res.status(400).json({ error: "Missing password" });
  }
  const valid = await bcrypt.compare(currentPassword, req.user.passwordHash);
  if (!valid) {
    return res.status(401).json({ error: "Invalid credentials" });
  }
  const validation = validatePasswordStrength(newPassword);
  if (!validation.ok) {
    return res.status(400).json({ error: validation.error });
  }
  const passwordHash = await bcrypt.hash(newPassword, 12);
  await prisma.user.update({
    where: { id: req.user.id },
    data: { passwordHash, passwordUpdatedAt: new Date(), mustChangePassword: false }
  });
  res.json({ ok: true });
});

app.get("/api/admin/settings", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const settings = await ensureSystemSettings();
  res.json({
    smtpHost: settings.smtpHost || "",
    smtpPort: settings.smtpPort || 587,
    smtpUser: settings.smtpUser || "",
    smtpFrom: settings.smtpFrom || "no-reply@voiceping.local",
    smtpPassSet: !!settings.smtpPassEncrypted
  });
});

app.patch("/api/admin/settings", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  await ensureSystemSettings();
  const { smtpHost, smtpPort, smtpUser, smtpPass, smtpFrom } = req.body || {};
  const data = {};
  if (smtpHost !== undefined) {
    data.smtpHost = normalizeEmpty(smtpHost);
  }
  if (smtpPort !== undefined) {
    const port = Number(smtpPort);
    data.smtpPort = Number.isNaN(port) ? null : port;
  }
  if (smtpUser !== undefined) {
    data.smtpUser = normalizeEmpty(smtpUser);
  }
  if (smtpFrom !== undefined) {
    data.smtpFrom = normalizeEmpty(smtpFrom);
  }
  if (smtpPass !== undefined) {
    data.smtpPassEncrypted = smtpPass === "" ? null : encryptSecret(smtpPass);
  }
  const settings = await prisma.systemSettings.update({ where: { id: SETTINGS_ID }, data });
  smtpConfigCache = null;
  smtpTransporter = null;
  res.json({
    smtpHost: settings.smtpHost || "",
    smtpPort: settings.smtpPort || 587,
    smtpUser: settings.smtpUser || "",
    smtpFrom: settings.smtpFrom || "no-reply@voiceping.local",
    smtpPassSet: !!settings.smtpPassEncrypted
  });
});

app.get("/api/admin/test-seed", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const seed = await findTestSeedEvent();
  const team = seed?.teams?.find((entry) => entry.name === TEST_SEED_TEAM_NAME) || null;
  const channel =
    seed?.channels?.find((entry) => entry.name === TEST_SEED_CHANNEL_NAME) ||
    seed?.channels?.find((entry) => entry.teamId === team?.id) ||
    null;
  res.json({
    enabled: TEST_SEED_ENABLED,
    event: seed ? { id: seed.id, name: seed.name } : null,
    team: team ? { id: team.id, name: team.name } : null,
    channel: channel ? { id: channel.id, name: channel.name } : null
  });
});

app.post("/api/admin/test-seed", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const seed = await ensureTestSeed();
  const team = seed?.teams?.find((entry) => entry.name === TEST_SEED_TEAM_NAME) || null;
  const channel =
    seed?.channels?.find((entry) => entry.name === TEST_SEED_CHANNEL_NAME) ||
    seed?.channels?.find((entry) => entry.teamId === team?.id) ||
    null;
  res.json({
    event: seed ? { id: seed.id, name: seed.name } : null,
    team: team ? { id: team.id, name: team.name } : null,
    channel: channel ? { id: channel.id, name: channel.name } : null
  });
});

app.delete("/api/admin/test-seed", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const removed = await removeTestSeed();
  res.json({ removed: !!removed });
});

app.get("/api/admin/status", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const payload = await buildStatusPayload();
  res.json(payload);
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
  const validation = validatePasswordStrength(password);
  if (!validation.ok) {
    return res.status(400).json({ error: validation.error });
  }
  const reset = await prisma.passwordReset.findUnique({ where: { token } });
  if (!reset || reset.usedAt || reset.expiresAt < new Date()) {
    return res.status(400).json({ error: "Invalid token" });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  await prisma.user.update({
    where: { id: reset.userId },
    data: { passwordHash, mustChangePassword: false, passwordUpdatedAt: new Date() }
  });
  await prisma.passwordReset.update({ where: { id: reset.id }, data: { usedAt: new Date() } });
  return res.json({ ok: true });
});

app.get("/api/admin/users", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const query = req.query.q;
  const where = query
    ? {
        OR: [
          { email: { contains: String(query), mode: "insensitive" } },
          { displayName: { contains: String(query), mode: "insensitive" } }
        ]
      }
    : undefined;
  const users = await prisma.user.findMany({
    where,
    orderBy: { createdAt: "desc" },
    select: {
      id: true,
      email: true,
      displayName: true,
      globalRole: true,
      mustChangePassword: true,
      lastLoginAt: true,
      disabledAt: true
    }
  });
  res.json(users);
});

app.post("/api/admin/users", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const { email, password, displayName, globalRole, mustChangePassword } = req.body || {};
  if (!email || !password) {
    return res.status(400).json({ error: "Missing email/password" });
  }
  const validation = validatePasswordStrength(password);
  if (!validation.ok) {
    return res.status(400).json({ error: validation.error });
  }
  const passwordHash = await bcrypt.hash(password, 12);
  const user = await prisma.user.create({
    data: {
      email,
      passwordHash,
      displayName: displayName || null,
      globalRole: globalRole === "ADMIN" ? "ADMIN" : "NONE",
      mustChangePassword: mustChangePassword !== false
    }
  });
  await audit(req.user.id, null, "user_created", { userId: user.id });
  res.json({ id: user.id, email: user.email, globalRole: user.globalRole });
});

app.patch("/api/admin/users/:userId", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
  const { userId } = req.params;
  const { displayName, globalRole, mustChangePassword, disabled } = req.body || {};
  const data = {};
  if (displayName !== undefined) {
    data.displayName = displayName ? String(displayName).trim() : null;
  }
  if (globalRole !== undefined) {
    data.globalRole = globalRole === "ADMIN" ? "ADMIN" : "NONE";
  }
  if (mustChangePassword !== undefined) {
    data.mustChangePassword = !!mustChangePassword;
  }
  if (disabled !== undefined) {
    data.disabledAt = disabled ? new Date() : null;
  }
  const user = await prisma.user.update({ where: { id: userId }, data });
  await audit(req.user.id, null, "user_updated", { userId: user.id });
  res.json({ id: user.id, email: user.email, displayName: user.displayName, globalRole: user.globalRole });
});

app.post("/api/events", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
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

app.patch("/api/events/:eventId", requireAuth, requireAdmin, requireProfileComplete, async (req, res) => {
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

app.get("/api/events", requireAuth, requireProfileComplete, async (req, res) => {
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

app.post("/api/events/:eventId/teams", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.post("/api/teams/:teamId/channels", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.post("/api/events/:eventId/channels", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.post("/api/events/:eventId/users", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const { userId, role, teamIds, channelIds } = req.body || {};
  if (!userId || !role) {
    return res.status(400).json({ error: "Missing userId/role" });
  }
  const user = await prisma.user.findUnique({ where: { id: userId } });
  if (!user || user.disabledAt) {
    return res.status(400).json({ error: "User not available for assignment" });
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

app.patch("/api/events/:eventId/users/:userId/approve", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.patch("/api/events/:eventId/users/:userId/channels", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.patch("/api/events/:eventId/users/:userId/teams", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.post("/api/events/:eventId/invites", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
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

app.post("/api/invites/:token/join", requireAuth, requireProfileComplete, async (req, res) => {
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

app.get("/api/events/:eventId/overview", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const event = await prisma.event.findUnique({ where: { id: eventId } });
  const teams = await prisma.team.findMany({ where: { eventId }, orderBy: { sortOrder: "asc" } });
  const channels = await prisma.channel.findMany({ where: { eventId }, orderBy: { sortOrder: "asc" } });
  const listenerMemberships = await prisma.channelMembership.findMany({
    where: { userId: req.user.id, status: "ACTIVE", channel: { eventId } },
    select: { channelId: true }
  });
  const listenerChannelIds = listenerMemberships.map((entry) => entry.channelId);
  const teamMemberCounts = await prisma.teamMembership.groupBy({
    by: ["teamId"],
    _count: { teamId: true },
    where: { team: { eventId }, status: "ACTIVE" }
  });
  const channelMemberCounts = await prisma.channelMembership.groupBy({
    by: ["channelId"],
    _count: { channelId: true },
    where: { channel: { eventId }, status: "ACTIVE" }
  });
  const memberships = await prisma.eventMembership.findMany({
    where: { eventId },
    include: { user: true }
  });
  const teamMemberships = await prisma.teamMembership.findMany({
    where: { team: { eventId }, status: "ACTIVE" },
    select: { teamId: true, userId: true }
  });
  const channelMemberships = await prisma.channelMembership.findMany({
    where: { channel: { eventId }, status: "ACTIVE" },
    select: { channelId: true, userId: true }
  });
  const pending = memberships.filter((m) => m.status === "PENDING");
  const now = Date.now();
  const channelIds = channels.map((channel) => channel.id);
  const latestMessages = await Promise.all(
    channelIds.map((channelId) =>
      redisClient
        .lRange(`._g_${channelId}`, 0, ACTIVITY_SAMPLE_SIZE - 1)
        .catch(() => [])
    )
  );
  const channelLastActivity = new Map();
  const userLastActivity = new Map();
  latestMessages.forEach((messages, index) => {
    const channelId = channelIds[index];
    if (!Array.isArray(messages)) {
      return;
    }
    messages.forEach((messageId) => {
      const meta = parseMessageMeta(messageId);
      if (!meta) {
        return;
      }
      const previousChannel = channelLastActivity.get(channelId);
      if (!previousChannel || meta.timestamp > previousChannel) {
        channelLastActivity.set(channelId, meta.timestamp);
      }
      const previousUser = userLastActivity.get(meta.fromId);
      if (!previousUser || meta.timestamp > previousUser) {
        userLastActivity.set(meta.fromId, meta.timestamp);
      }
    });
  });
  const activeTeamMembers = teamMemberships.reduce((acc, entry) => {
    if (!acc.has(entry.teamId)) {
      acc.set(entry.teamId, new Set());
    }
    acc.get(entry.teamId).add(entry.userId);
    return acc;
  }, new Map());
  const activeChannelMembers = channelMemberships.reduce((acc, entry) => {
    if (!acc.has(entry.channelId)) {
      acc.set(entry.channelId, new Set());
    }
    acc.get(entry.channelId).add(entry.userId);
    return acc;
  }, new Map());
  const userStatuses = memberships.reduce((acc, membership) => {
    const isActiveMember = membership.status === "ACTIVE";
    const lastActiveAt = userLastActivity.get(membership.user.id);
    const hasTraffic = isActiveMember && lastActiveAt && now - lastActiveAt <= ACTIVE_TRAFFIC_WINDOW_MS;
    acc[membership.user.id] = deriveStatus(hasTraffic, isActiveMember);
    return acc;
  }, {});
  const channelStatuses = channels.reduce((acc, channel) => {
    const activeCount = activeChannelMembers.get(channel.id)?.size || 0;
    const lastActiveAt = channelLastActivity.get(channel.id);
    const hasTraffic = lastActiveAt && now - lastActiveAt <= ACTIVE_TRAFFIC_WINDOW_MS;
    acc[channel.id] = deriveStatus(hasTraffic, activeCount > 0);
    return acc;
  }, {});
  const teamStatuses = teams.reduce((acc, team) => {
    const activeCount = activeTeamMembers.get(team.id)?.size || 0;
    const teamChannels = channels.filter((channel) => channel.teamId === team.id);
    const hasTraffic = teamChannels.some((channel) => channelStatuses[channel.id] === "ACTIVE");
    acc[team.id] = deriveStatus(hasTraffic, activeCount > 0);
    return acc;
  }, {});
  res.json({
    event,
    teams,
    channels,
    listenerChannelIds,
    teamMemberCounts: teamMemberCounts.reduce((acc, entry) => {
      acc[entry.teamId] = entry._count.teamId;
      return acc;
    }, {}),
    channelMemberCounts: channelMemberCounts.reduce((acc, entry) => {
      acc[entry.channelId] = entry._count.channelId;
      return acc;
    }, {}),
    roster: memberships.map((m) => ({
      id: m.user.id,
      email: m.user.email,
      displayName: m.user.displayName,
      role: m.role,
      status: m.status
    })),
    pendingCount: pending.length,
    statuses: {
      users: userStatuses,
      teams: teamStatuses,
      channels: channelStatuses
    }
  });
});

app.get("/api/events/:eventId/traffic", requireAuth, requireProfileComplete, requireDispatchOrAdmin, async (req, res) => {
  const { eventId } = req.params;
  const channels = await prisma.channel.findMany({
    where: { eventId },
    select: { id: true },
    orderBy: { sortOrder: "asc" }
  });
  const now = Date.now();
  const traffic = await Promise.all(
    channels.map(async (channel) => {
      const key = `g:${channel.id}:m`;
      let data = {};
      try {
        data = await redisClient.hGetAll(key);
      } catch (err) {
        data = {};
      }
      const audioTime = data.audioTime ? Number(data.audioTime) : null;
      const active = !!audioTime && now - audioTime < MAXIMUM_IDLE_DURATION;
      return {
        channelId: channel.id,
        active,
        audioTime,
        fromId: data.fromId || null
      };
    })
  );
  res.json({ channels: traffic, evaluatedAt: now });
});

app.post("/api/router/token", requireAuth, requireProfileComplete, async (req, res) => {
  const { eventId } = req.body || {};
  if (!eventId) {
    return res.status(400).json({ error: "Missing eventId" });
  }

  const isGlobalAdmin = req.user.globalRole === "ADMIN";

  // ADMIN users bypass event membership requirement — full access to all events
  let role;
  let channelIds;
  let channelsData;

  if (isGlobalAdmin) {
    role = "ADMIN";
    // Get ALL channels in the event
    channelsData = await prisma.channel.findMany({
      where: { eventId },
      select: { id: true, name: true }
    });
    channelIds = channelsData.map((c) => c.id);
  } else {
    const membership = await prisma.eventMembership.findUnique({
      where: { userId_eventId: { userId: req.user.id, eventId } }
    });
    if (!membership || membership.status !== "ACTIVE") {
      return res.status(403).json({ error: "Not active in event" });
    }
    role = membership.role;
    const channelMemberships = await prisma.channelMembership.findMany({
      where: { userId: req.user.id, status: "ACTIVE", channel: { eventId } },
      select: { channelId: true }
    });
    channelIds = channelMemberships.map((entry) => entry.channelId);
    channelsData = await prisma.channel.findMany({
      where: { id: { in: channelIds } },
      select: { id: true, name: true }
    });
  }

  const channelNames = Object.fromEntries(channelsData.map((c) => [c.id, c.name]));

  const token = jwt.sign(
    {
      userId: req.user.id,
      userName: req.user.displayName || req.user.email,
      globalRole: req.user.globalRole,
      eventId,
      role,
      channelIds
    },
    ROUTER_JWT_SECRET,
    { expiresIn: "12h" }
  );
  res.json({ token, channelNames });
});

const waitForDatabase = async () => {
  while (!readiness.database.ok) {
    await new Promise((resolve) => setTimeout(resolve, 500));
  }
};

const startServer = () => {
  startDatabase();
  startRedis();
  app.listen(port, () => {
    console.log(`Control plane API listening on port ${port}`);
  });
  waitForDatabase()
    .then(async () => {
      await bootstrapAdmin();
      if (TEST_SEED_ENABLED) {
        try {
          const seed = await ensureTestSeed();
          const team = seed?.teams?.find((entry) => entry.name === TEST_SEED_TEAM_NAME) || null;
          const channel =
            seed?.channels?.find((entry) => entry.name === TEST_SEED_CHANNEL_NAME) ||
            seed?.channels?.find((entry) => entry.teamId === team?.id) ||
            null;
          console.log(
            `[test-seed] event=${seed?.id || "none"} team=${team?.id || "none"} channel=${channel?.id || "none"}`
          );
        } catch (err) {
          console.error("[test-seed] failed to seed", err);
        }
      }
    })
    .catch((err) => {
      console.error("Bootstrap admin failed", err);
    });
};

startServer();
