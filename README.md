voiceping-router
================

### This is the server for [VoicePing Android SDK](https://github.com/SmartWalkieOrg/VoicePingAndroidSDK)

A real-time push-to-talk server to route and broadcast voice messaging. The server is using NodeJS with native websocket, and also using Redis as temporary storage.

You may want to try first, you could try to connect the SDK to `wss://router-lite.voiceping.info`

# Running the service

## Requirements
In order to run the server, you need to have at least:
* Ubuntu 24.04 LTS (or newer)
* **NodeJS 8.16.0** (this needs to be exact, higher nodejs/npm will probably cause installation and run issue)
* Redis
* Docker (optional if you want run in docker)

## Environment variables
To use environment variable, simply copy `.env.example` into `.env` and adjust the value accordingly.

Below are the required environment variables that need to be set before running the server.

Server related:

* `PORT` (int): The port number the server will listen to. Default: `3000`.
* `USE_AUTHENTICATION` (boolean): The configuration whether you want to use JWT authentication or not. Default: `false`
* `SECRET_KEY` (string): JWT secret key. This is required when `USE_AUTHENTICATION` is set true.

Database related:

* `REDIS_HOST` (string): Redis host. Default: `localhost`.
* `REDIS_PORT` (string): Redis host. Default: `6379`.
* `REDIS_PASSWORD` (string): Redis host. Default: `localhost`.

## Run the server ##

### Production

Install dependencies with `npm` and start

    $ npm install
    $ npm run build
    $ npm run start

The server should be running on `ws://localhost:3000` by default. You can check in browser using `http://<your-ip-or-hostname>` it should be showing something like this:
```
Welcome to VoicePing Router 1.0.0
```

### Development
Start development server with `nodemon`. It will automatically watch file changes, lint it and restart the server.

    $ npm run dev



## Test ##

### Unit Test

Run test with npm

    $ npm test

### Manual Test
To run properly, voiceping-router needs to run together with redis (by default on port 6379). So you need to run redis on OS port 6379. Easiest with docker (using the repo's Redis config):

    $ docker run -p 6379:6379 \
      -v $(pwd)/redis/redis.conf:/usr/local/etc/redis/redis.conf:ro \
      --name voiceping-redis \
      -d redis \
      redis-server /usr/local/etc/redis/redis.conf

In order to test whether this router server is running correctly. You need to use it from Android SDK. We also provide a simple web page for you to test: https://voiceping-router-test.netlify.app. Input any company name and user ID. Router URL input will be something like `ws://localhost:3000` or `ws://127.0.0.1:3000` depends on your own IP and port setting.

## Running in Docker

The easiest way to run voiceping-server using a single command is by using Docker Compose. Simply run the command below

    $ docker compose up -d

The compose file will also start Redis using the repo's configuration file at `redis/redis.conf`, which disables persistence (VoicePing Router uses Redis as temporary storage) and sets an LRU eviction policy. It also boots the control-plane API (port 4000), a Postgres instance for the control plane, and the dispatch web UI (port 8080).

If you need to customize Redis settings, edit `redis/redis.conf` and restart the compose stack.

Same as above. When you access `http://<your-ip-or-hostname>` from your browser, you should see:

```
Welcome to VoicePing Router 1.0.0
```

For the dispatch console MVP, open `http://localhost:8080` and the control-plane API will answer on `http://localhost:4000`.

### Ubuntu 24.04 LTS Docker smoke test
On Ubuntu 24.04 LTS, you can do a quick smoke test of the Docker stack with:

    $ docker compose up -d
    $ docker compose ps
    $ curl -s http://localhost:3000

You should see `Welcome to VoicePing Router 1.0.0` in the response. If you need to stop the stack:

    $ docker compose down

## Self-hosted VoicePing Router

If you choose to self-host the VoicePing Router, you will need to update the server URL **on your app that uses the VoicePing Android SDK** to your new self-hosted domain or IP address. 

## User Authentication

By default, this server doesn't use any authentication. But you can enable authentication by setting thru environment variables. Below are the environment variables that you need to set:
- USE_AUTHENTICATION
- SECRET_KEY

Once you set that, the server will require JWT authentication in order to receive connection from client.

#### Encode JWT in Your Client / Server
Instead of using `user_id` to connect to voiceping-router server. You need to decode the whole user information to JWT token. You can do this either in your client or in your own server.

Example in Javascript
```
var jwt = require('jsonwebtoken');
var SECRET_KEY = 'something';

var user = {
  user_id: 1,
  name: 'John'
}
var token = jwt.sign(user, SECRET_KEY);
```

#### Connect to VoicePing Router Using Encoded Token
Once you get the encode the user information into JWT. You can connect to voiceping-router using that token.

Example using Javascript:
```
var WebSocket = require("ws");

var connection = new WebSocket(WS_URL, [VoicePingToken, DeviceId]);

connection.on("open", () => {
  console.log("connection.on.open");
});
```

On VoicePing SDK demo apk, the token is simply concatenation of company name and user id. Something like: `${companyName}_${userId}`
