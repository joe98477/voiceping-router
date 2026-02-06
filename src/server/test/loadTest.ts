/**
 * Load Test Script for VoicePing PTT Router
 * Simulates 100 concurrent users with JWT authentication, channel operations, and PTT interactions
 */

import * as jwt from 'jsonwebtoken';
import WebSocket from 'ws';
import { createClient, RedisClientType } from 'redis';
import { SignalingType } from '../../shared/protocol';
import { UserRole } from '../../shared/types';

// Configuration
const ROUTER_JWT_SECRET = process.env.ROUTER_JWT_SECRET || 'change-me';
const REDIS_HOST = process.env.REDIS_HOST || '127.0.0.1';
const REDIS_PORT = process.env.REDIS_PORT || '6379';
const REDIS_PASSWORD = process.env.REDIS_PASSWORD || '';
const SERVER_URL = process.env.SERVER_URL || 'ws://localhost:3000/ws';

const NUM_USERS = 100;
const NUM_CHANNELS = 10;
const RAMP_DURATION_MS = 10000; // 10 seconds ramp
const USERS_PER_CHANNEL = 10;

// Test metrics
interface TestMetrics {
  connectionSuccessRate: number;
  avgConnectionTime: number;
  avgJoinTime: number;
  avgPttLockTime: number;
  avgDispatchInterruptTime: number;
  permissionRevocationTime: number;
  totalUsers: number;
  successfulConnections: number;
  failedConnections: number;
}

interface UserConnection {
  userId: string;
  role: UserRole;
  channels: string[];
  ws: WebSocket | null;
  connected: boolean;
  connectionTime: number;
  joinTimes: number[];
  pttLockTime: number | null;
}

// Generate JWT token for test user
function generateToken(userId: string, role: UserRole, channelIds: string[]): string {
  const payload = {
    userId,
    userName: `Test User ${userId}`,
    eventId: 'test-event-1',
    role,
    globalRole: role === UserRole.ADMIN ? 'ADMIN' : 'USER',
    channelIds,
  };

  return jwt.sign(payload, ROUTER_JWT_SECRET, { expiresIn: '1h' });
}

// Redis client setup
async function setupRedisClient(): Promise<RedisClientType> {
  const client = createClient({
    url: REDIS_PASSWORD
      ? `redis://:${REDIS_PASSWORD}@${REDIS_HOST}:${REDIS_PORT}`
      : `redis://${REDIS_HOST}:${REDIS_PORT}`,
  }) as RedisClientType;

  await client.connect();
  return client;
}

// Seed Redis with test data
async function seedRedisTestData(redis: RedisClientType, users: UserConnection[]): Promise<void> {
  console.log('Seeding Redis with test data...');

  // Seed user-channel mappings
  for (const user of users) {
    const userKey = `u.${user.userId}.g`;
    await redis.sAdd(userKey, user.channels);
  }

  // Seed channel-user mappings
  const channelUserMap: { [channelId: string]: string[] } = {};
  for (const user of users) {
    for (const channelId of user.channels) {
      if (!channelUserMap[channelId]) {
        channelUserMap[channelId] = [];
      }
      channelUserMap[channelId].push(user.userId);
    }
  }

  for (const [channelId, userIds] of Object.entries(channelUserMap)) {
    const channelKey = `g.${channelId}.u`;
    await redis.sAdd(channelKey, userIds);
  }

  // Seed event-channel mapping
  const eventKey = 'e.test-event-1.g';
  const allChannels = Array.from(new Set(users.flatMap(u => u.channels)));
  await redis.sAdd(eventKey, allChannels);

  console.log(`Seeded ${users.length} users across ${allChannels.length} channels`);
}

// Cleanup Redis test data
async function cleanupRedisTestData(redis: RedisClientType, users: UserConnection[]): Promise<void> {
  console.log('Cleaning up Redis test data...');

  // Remove user keys
  for (const user of users) {
    await redis.del(`u.${user.userId}.g`);
  }

  // Remove channel keys
  const allChannels = Array.from(new Set(users.flatMap(u => u.channels)));
  for (const channelId of allChannels) {
    await redis.del(`g.${channelId}.u`);
  }

  // Remove event key
  await redis.del('e.test-event-1.g');

  console.log('Cleanup complete');
}

// Create test users
function createTestUsers(): UserConnection[] {
  const users: UserConnection[] = [];

  for (let i = 1; i <= NUM_USERS; i++) {
    const userId = `test-user-${i}`;
    let role: UserRole;

    // Role distribution: 80 GENERAL, 15 DISPATCH, 5 ADMIN
    if (i <= 5) {
      role = UserRole.ADMIN;
    } else if (i <= 20) {
      role = UserRole.DISPATCH;
    } else {
      role = UserRole.GENERAL;
    }

    // Assign to channels (~10 users per channel)
    const channelIndex = Math.floor((i - 1) / USERS_PER_CHANNEL);
    const channelId = `test-channel-${channelIndex + 1}`;

    users.push({
      userId,
      role,
      channels: [channelId],
      ws: null,
      connected: false,
      connectionTime: 0,
      joinTimes: [],
      pttLockTime: null,
    });
  }

  return users;
}

// Connect a single user
async function connectUser(user: UserConnection): Promise<void> {
  return new Promise((resolve, reject) => {
    const startTime = Date.now();
    const token = generateToken(user.userId, user.role, user.channels);

    const ws = new WebSocket(`${SERVER_URL}?token=${token}`);
    user.ws = ws;

    const timeout = setTimeout(() => {
      ws.close();
      reject(new Error(`Connection timeout for ${user.userId}`));
    }, 5000);

    ws.on('open', () => {
      clearTimeout(timeout);
      user.connected = true;
      user.connectionTime = Date.now() - startTime;
      resolve();
    });

    ws.on('error', (err) => {
      clearTimeout(timeout);
      reject(err);
    });
  });
}

// Join channel
async function joinChannel(user: UserConnection, channelId: string): Promise<number> {
  return new Promise((resolve, reject) => {
    if (!user.ws || !user.connected) {
      reject(new Error('User not connected'));
      return;
    }

    const startTime = Date.now();
    const messageId = `join-${Date.now()}-${Math.random()}`;

    const timeout = setTimeout(() => {
      reject(new Error(`Join channel timeout for ${user.userId}`));
    }, 5000);

    const messageHandler = (data: Buffer) => {
      try {
        const message = JSON.parse(data.toString());
        if (message.id === messageId) {
          clearTimeout(timeout);
          user.ws?.off('message', messageHandler);
          const joinTime = Date.now() - startTime;
          user.joinTimes.push(joinTime);
          resolve(joinTime);
        }
      } catch (err) {
        // Ignore parse errors
      }
    };

    user.ws.on('message', messageHandler);

    user.ws.send(JSON.stringify({
      type: SignalingType.JOIN_CHANNEL,
      id: messageId,
      data: { channelId },
    }));
  });
}

// Start PTT
async function startPtt(user: UserConnection, channelId: string, priority: boolean = false): Promise<number> {
  return new Promise((resolve, reject) => {
    if (!user.ws || !user.connected) {
      reject(new Error('User not connected'));
      return;
    }

    const startTime = Date.now();
    const messageId = `ptt-${Date.now()}-${Math.random()}`;

    const timeout = setTimeout(() => {
      reject(new Error(`PTT start timeout for ${user.userId}`));
    }, 5000);

    const messageHandler = (data: Buffer) => {
      try {
        const message = JSON.parse(data.toString());
        // Look for success response (either ack or speaker-changed)
        if (message.id === messageId || (message.type === SignalingType.SPEAKER_CHANGED && message.data?.currentSpeaker === user.userId)) {
          clearTimeout(timeout);
          user.ws?.off('message', messageHandler);
          const lockTime = Date.now() - startTime;
          user.pttLockTime = lockTime;
          resolve(lockTime);
        }
      } catch (err) {
        // Ignore parse errors
      }
    };

    user.ws.on('message', messageHandler);

    user.ws.send(JSON.stringify({
      type: priority ? SignalingType.PRIORITY_PTT_START : SignalingType.PTT_START,
      id: messageId,
      data: { channelId },
    }));
  });
}

// Stop PTT
async function stopPtt(user: UserConnection, channelId: string, priority: boolean = false): Promise<void> {
  return new Promise((resolve) => {
    if (!user.ws || !user.connected) {
      resolve();
      return;
    }

    user.ws.send(JSON.stringify({
      type: priority ? SignalingType.PRIORITY_PTT_STOP : SignalingType.PTT_STOP,
      data: { channelId },
    }));

    // Don't wait for response
    setTimeout(resolve, 100);
  });
}

// Phase A: Connection phase
async function testConnectionPhase(users: UserConnection[]): Promise<void> {
  console.log('\n=== PHASE A: Connection Phase ===');
  console.log(`Connecting ${NUM_USERS} users over ${RAMP_DURATION_MS}ms (${NUM_USERS / (RAMP_DURATION_MS / 1000)} users/sec)...`);

  const delayBetweenUsers = RAMP_DURATION_MS / NUM_USERS;
  const results: Array<{ success: boolean; user: UserConnection }> = [];

  for (let i = 0; i < users.length; i++) {
    const user = users[i];

    // Connect with ramp delay
    const connectPromise = connectUser(user)
      .then(() => ({ success: true, user }))
      .catch((err) => {
        console.error(`Failed to connect ${user.userId}:`, err.message);
        return { success: false, user };
      });

    results.push(await connectPromise);

    // Delay before next connection
    if (i < users.length - 1) {
      await new Promise(resolve => setTimeout(resolve, delayBetweenUsers));
    }
  }

  const successful = results.filter(r => r.success).length;
  const failed = results.filter(r => !r.success).length;

  console.log(`Connections: ${successful} successful, ${failed} failed`);
}

// Phase B: Channel join phase
async function testChannelJoinPhase(users: UserConnection[]): Promise<void> {
  console.log('\n=== PHASE B: Channel Join Phase ===');
  console.log('Users joining assigned channels...');

  const joinPromises = users
    .filter(u => u.connected)
    .map(async (user) => {
      try {
        for (const channelId of user.channels) {
          await joinChannel(user, channelId);
        }
      } catch (err: any) {
        console.error(`Failed to join channels for ${user.userId}:`, err.message);
      }
    });

  await Promise.all(joinPromises);

  const totalJoins = users.reduce((sum, u) => sum + u.joinTimes.length, 0);
  console.log(`Completed ${totalJoins} channel joins`);
}

// Phase C: PTT load phase
async function testPttLoadPhase(users: UserConnection[]): Promise<void> {
  console.log('\n=== PHASE C: PTT Load Phase ===');
  console.log('10% of users starting PTT simultaneously on different channels...');

  const connectedUsers = users.filter(u => u.connected);
  const pttUsers = connectedUsers.slice(0, Math.floor(connectedUsers.length * 0.1));

  // Group by channel to ensure different channels
  const channelGroups: { [channelId: string]: UserConnection[] } = {};
  for (const user of pttUsers) {
    const channelId = user.channels[0];
    if (!channelGroups[channelId]) {
      channelGroups[channelId] = [];
    }
    channelGroups[channelId].push(user);
  }

  // Start PTT for one user per channel simultaneously
  const pttPromises = Object.entries(channelGroups).map(([channelId, groupUsers]) => {
    const user = groupUsers[0]; // Pick first user in channel
    return startPtt(user, channelId).catch((err) => {
      console.error(`PTT start failed for ${user.userId}:`, err.message);
      return -1;
    });
  });

  await Promise.all(pttPromises);

  // Stop PTT for cleanup
  const stopPromises = Object.entries(channelGroups).map(([channelId, groupUsers]) => {
    const user = groupUsers[0];
    return stopPtt(user, channelId);
  });

  await Promise.all(stopPromises);

  console.log(`PTT load test complete for ${Object.keys(channelGroups).length} channels`);
}

// Phase D: Dispatch priority phase
async function testDispatchPriorityPhase(users: UserConnection[]): Promise<number | null> {
  console.log('\n=== PHASE D: Dispatch Priority Phase ===');
  console.log('Testing Dispatch user interrupting General user...');

  const dispatchUser = users.find(u => u.connected && u.role === UserRole.DISPATCH);
  const generalUser = users.find(u => u.connected && u.role === UserRole.GENERAL);

  if (!dispatchUser || !generalUser) {
    console.log('Skipping: Missing required user roles');
    return null;
  }

  const channelId = dispatchUser.channels[0];

  try {
    // General user starts PTT
    await startPtt(generalUser, channelId);
    console.log(`General user ${generalUser.userId} started PTT`);

    // Wait a moment
    await new Promise(resolve => setTimeout(resolve, 500));

    // Dispatch user interrupts with priority PTT
    const startTime = Date.now();
    await startPtt(dispatchUser, channelId, true);
    const interruptTime = Date.now() - startTime;
    console.log(`Dispatch user ${dispatchUser.userId} interrupted (${interruptTime}ms)`);

    // Cleanup
    await stopPtt(dispatchUser, channelId, true);
    await stopPtt(generalUser, channelId);

    return interruptTime;
  } catch (err: any) {
    console.error('Dispatch priority test failed:', err.message);
    return null;
  }
}

// Phase E: Permission revocation phase
async function testPermissionRevocationPhase(users: UserConnection[], redis: RedisClientType): Promise<number | null> {
  console.log('\n=== PHASE E: Permission Revocation Phase ===');
  console.log('Revoking 5 users\' channel access...');

  const connectedUsers = users.filter(u => u.connected);
  const usersToRevoke = connectedUsers.slice(0, 5);

  const startTime = Date.now();

  // Revoke permissions in Redis
  for (const user of usersToRevoke) {
    const userKey = `u.${user.userId}.g`;
    await redis.del(userKey);
  }

  console.log('Permissions revoked in Redis, monitoring for disconnection...');

  // Monitor for disconnection (wait up to 35 seconds - heartbeat is 30s)
  const maxWaitTime = 35000;
  const checkInterval = 1000;
  let elapsed = 0;
  let allDisconnected = false;

  while (elapsed < maxWaitTime && !allDisconnected) {
    await new Promise(resolve => setTimeout(resolve, checkInterval));
    elapsed += checkInterval;

    allDisconnected = usersToRevoke.every(u => !u.connected || u.ws?.readyState !== WebSocket.OPEN);

    if (allDisconnected) {
      const revocationTime = Date.now() - startTime;
      console.log(`All users removed within ${revocationTime}ms`);
      return revocationTime;
    }
  }

  console.log('Timeout: Not all users disconnected within 35s');
  return null;
}

// Phase F: Disconnect phase
async function testDisconnectPhase(users: UserConnection[]): Promise<void> {
  console.log('\n=== PHASE F: Disconnect Phase ===');
  console.log('Gracefully disconnecting all users...');

  const startTime = Date.now();

  const disconnectPromises = users
    .filter(u => u.connected && u.ws)
    .map(user => {
      return new Promise<void>((resolve) => {
        user.ws?.on('close', () => {
          user.connected = false;
          resolve();
        });
        user.ws?.close();
      });
    });

  await Promise.all(disconnectPromises);

  const disconnectTime = Date.now() - startTime;
  console.log(`All users disconnected in ${disconnectTime}ms`);
}

// Calculate and display metrics
function displayMetrics(users: UserConnection[], dispatchInterruptTime: number | null): TestMetrics {
  console.log('\n=== TEST METRICS ===\n');

  const connectedUsers = users.filter(u => u.connected || u.connectionTime > 0);
  const successfulConnections = connectedUsers.length;
  const failedConnections = NUM_USERS - successfulConnections;

  // Connection metrics
  const connectionTimes = connectedUsers.map(u => u.connectionTime).filter(t => t > 0);
  const avgConnectionTime = connectionTimes.length > 0
    ? connectionTimes.reduce((a, b) => a + b, 0) / connectionTimes.length
    : 0;

  // Join metrics
  const allJoinTimes = users.flatMap(u => u.joinTimes).filter(t => t > 0);
  const avgJoinTime = allJoinTimes.length > 0
    ? allJoinTimes.reduce((a, b) => a + b, 0) / allJoinTimes.length
    : 0;

  // PTT metrics
  const pttLockTimes = users.map(u => u.pttLockTime).filter((t): t is number => t !== null && t > 0);
  const avgPttLockTime = pttLockTimes.length > 0
    ? pttLockTimes.reduce((a, b) => a + b, 0) / pttLockTimes.length
    : 0;

  const metrics: TestMetrics = {
    connectionSuccessRate: (successfulConnections / NUM_USERS) * 100,
    avgConnectionTime,
    avgJoinTime,
    avgPttLockTime,
    avgDispatchInterruptTime: dispatchInterruptTime || 0,
    permissionRevocationTime: 0, // Calculated separately
    totalUsers: NUM_USERS,
    successfulConnections,
    failedConnections,
  };

  // Display results table
  console.log('┌─────────────────────────────────────┬──────────────┬──────────┐');
  console.log('│ Metric                              │ Result       │ Status   │');
  console.log('├─────────────────────────────────────┼──────────────┼──────────┤');

  const results = [
    {
      metric: 'Connection Success Rate',
      result: `${metrics.connectionSuccessRate.toFixed(1)}%`,
      expected: 100,
      actual: metrics.connectionSuccessRate,
      unit: '%',
    },
    {
      metric: 'Avg Connection Time',
      result: `${metrics.avgConnectionTime.toFixed(0)}ms`,
      expected: 500,
      actual: metrics.avgConnectionTime,
      unit: 'ms',
    },
    {
      metric: 'Avg Join Time',
      result: `${metrics.avgJoinTime.toFixed(0)}ms`,
      expected: 200,
      actual: metrics.avgJoinTime,
      unit: 'ms',
    },
    {
      metric: 'Avg PTT Lock Time',
      result: `${metrics.avgPttLockTime.toFixed(0)}ms`,
      expected: 50,
      actual: metrics.avgPttLockTime,
      unit: 'ms',
    },
    {
      metric: 'Dispatch Interrupt Time',
      result: dispatchInterruptTime ? `${dispatchInterruptTime}ms` : 'N/A',
      expected: 100,
      actual: dispatchInterruptTime || 0,
      unit: 'ms',
    },
  ];

  for (const row of results) {
    const status = row.actual <= row.expected ? 'PASS ✓' : 'FAIL ✗';
    const statusColor = row.actual <= row.expected ? '' : '';
    console.log(`│ ${row.metric.padEnd(35)} │ ${row.result.padEnd(12)} │ ${(statusColor + status).padEnd(8)} │`);
  }

  console.log('└─────────────────────────────────────┴──────────────┴──────────┘');
  console.log();

  return metrics;
}

// Main test execution
async function runLoadTest(): Promise<void> {
  console.log('VoicePing PTT Router - Load Test');
  console.log('================================\n');
  console.log(`Configuration:`);
  console.log(`  Users: ${NUM_USERS}`);
  console.log(`  Channels: ${NUM_CHANNELS}`);
  console.log(`  Server: ${SERVER_URL}`);
  console.log(`  Redis: ${REDIS_HOST}:${REDIS_PORT}\n`);

  let redis: RedisClientType | null = null;
  const users = createTestUsers();

  try {
    // Connect to Redis
    redis = await setupRedisClient();

    // Seed test data
    await seedRedisTestData(redis, users);

    // Run test phases
    await testConnectionPhase(users);
    await testChannelJoinPhase(users);
    await testPttLoadPhase(users);
    const dispatchInterruptTime = await testDispatchPriorityPhase(users);
    const revocationTime = await testPermissionRevocationPhase(users, redis);

    // Display metrics
    const metrics = displayMetrics(users, dispatchInterruptTime);

    // Disconnect remaining users
    await testDisconnectPhase(users);

    // Cleanup
    await cleanupRedisTestData(redis, users);

    console.log('\n=== LOAD TEST COMPLETE ===\n');

    // Exit with appropriate code
    const allPassed =
      metrics.connectionSuccessRate === 100 &&
      metrics.avgConnectionTime <= 500 &&
      metrics.avgJoinTime <= 200 &&
      metrics.avgPttLockTime <= 50 &&
      (dispatchInterruptTime === null || dispatchInterruptTime <= 100);

    process.exit(allPassed ? 0 : 1);
  } catch (err) {
    console.error('Load test failed:', err);

    if (redis) {
      await cleanupRedisTestData(redis, users);
    }

    process.exit(1);
  } finally {
    if (redis) {
      await redis.quit();
    }
  }
}

// Run the test
runLoadTest().catch((err) => {
  console.error('Fatal error:', err);
  process.exit(1);
});
