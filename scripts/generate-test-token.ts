/**
 * Generate JWT tokens for testing PTT functionality
 * Usage: tsx scripts/generate-test-token.ts --userId user1 --userName "Alice"
 */

import * as jwt from 'jsonwebtoken';
import * as dotenv from 'dotenv';

// Load environment variables
dotenv.config();

// Parse command line arguments
const args = process.argv.slice(2);
const getArg = (name: string, defaultValue: string): string => {
  const index = args.indexOf(`--${name}`);
  if (index !== -1 && index + 1 < args.length) {
    return args[index + 1];
  }
  return defaultValue;
};

const userId = getArg('userId', 'test-user-1');
const userName = getArg('userName', 'Test User');
const secret = getArg('secret', process.env.ROUTER_JWT_SECRET || 'change-me');
const expiryHours = parseInt(getArg('expiry', '1'), 10);

// Generate JWT payload
const payload = {
  userId,
  userName,
  exp: Math.floor(Date.now() / 1000) + (expiryHours * 3600),
};

// Sign the token
const token = jwt.sign(payload, secret);

// Output the token
console.log('Generated JWT token:');
console.log('');
console.log('User ID:', userId);
console.log('User Name:', userName);
console.log('Expires in:', expiryHours, 'hour(s)');
console.log('');
console.log('Token:');
console.log(token);
console.log('');
console.log('To test with two users, generate a second token:');
console.log('tsx scripts/generate-test-token.ts --userId user2 --userName "Bob"');
console.log('');
