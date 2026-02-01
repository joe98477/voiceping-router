import * as cluster from "cluster";

import * as dbug from "debug";
import * as _ from "lodash";
import * as Q from "q";
import * as WebSocket from "ws";
import * as jwt from "jwt-simple";

import ChannelType = require("./channeltype");
import Client, { IClients } from "./client";
import config = require("./config");

import logger = require("./logger");
import MessageType = require("./messagetype");
import { packer } from "./packer";
import Redis = require("./redis");
import States from "./states";
import { IMessage, numberOrString } from "./types";

const WORKER_NUMBER = cluster.worker ? cluster.worker.id : "-";
const dbug1 = dbug("vp:router");
function debug(msg: string) {
  dbug1((cluster.worker ? `worker ${cluster.worker.id} ` : "") + msg);
}

export interface IServer {
  sendMessageToUser: (mesage: IMessage) => void;
  sendMessageToGroup: (message: IMessage) => void;
}
interface IConnection {
  token: string;
  deviceId: string;
  key: string;
}

// class Server implements IServer {
class Server implements IServer {

  private clients: IClients = {};
  private sockets = {};
  private deviceTokens = {};
  private wss = null;
  private verify = null;

  constructor(options) {
    const opts = {
      memo: null,
      port: 9000,
      server: null,
      verify: this.verifyClient,
      ...options
    };

    if (opts.verify) { this.verify = opts.verify; }

    States.setMemored(opts.memo);
    States.periodicInspect();
    if (WORKER_NUMBER.toString() === "1") {
        Redis.periodicClean();
    }

    // WSS & WS SETUP
    if (opts.server) {
      this.wss = new WebSocket.Server({ server: opts.server, verifyClient: this.verify.bind(this) });
      logger.info("WebSocket.Server is created");
    } else {
      this.wss = new WebSocket.Server({ port: opts.port, verifyClient: this.verify.bind(this) });
      logger.info(`WebSocket.Server is created at port ${opts.port}`);
    }

    this.wss.on("connection", this.handleWssConnection.bind(this));

    Redis.subscribeMembershipUpdates((payload) => {
      if (!payload || payload.action !== "set_user_channels") { return; }
      const userId = payload.userId;
      const channelIds = payload.channelIds || [];
      States.getGroupsOfUser(userId, (err, currentGroupIds) => {
        const current = currentGroupIds || [];
        const next = channelIds.map((id) => id + "");
        current.forEach((groupId) => {
          if (!next.includes(groupId)) {
            States.removeUserFromGroup(userId, groupId);
          }
        });
        next.forEach((groupId) => {
          States.addUserToGroup(userId, groupId);
        });
      });
    });
  }

  // IServer Implementation

  public sendMessageToUser(this: Server, msg: IMessage) {
    packer.pack(msg, (err, packed) => {
      const client = this.clients[msg.toId];
      if (!client) {
        if (msg.messageType === MessageType.AUDIO) {
          debug(`sendMessageToUser type AUDIO NOT-FOUND id ${msg.toId} ${JSON.stringify(msg)}`);
        } else {
          debug(`sendMessageToUser type NON-AUDIO NOT-FOUND id ${msg.toId} ${JSON.stringify(msg)}`);
        }
        return;
      }
      client.send(packed);
    });
  }

  public sendMessageToGroup(this: Server, msg: IMessage) {
    logger.info(`sendMessageToGroup from: ${msg.fromId} to: ${msg.toId} messageType: ${msg.messageType}`);
    Redis.getUsersInsideGroup(msg.toId, (err, userIds) => {
      return States.getUsersInsideGroup(msg.toId, (err1, stateUserIds) => {
        const redisUsers = userIds && userIds instanceof Array ? userIds : [];
        const stateUsers = stateUserIds && stateUserIds instanceof Array ? stateUserIds : [];
        const combined = Array.from(new Set([...redisUsers, ...stateUsers]));
        if (err || err1 || combined.length === 0) {
          return this.broadcastToGroupWithCheck(msg, combined);
        }
        return this.broadcastToGroupWithCheck(msg, combined);
      });
    });
  }

  private handleClientUnregister = (client: Client) => {
    const clientId = client.id;
    if (!this.clients[clientId]) { return; }

    client.removeListener("message", this.handleClientMessage);
    client.removeListener("unregister", this.handleClientUnregister);
    delete this.clients[clientId];
    delete this.sockets[clientId];
    logger.info(`UNREGISTERED id ${clientId} clients ${Object.keys(this.clients).length}` +
                ` sockets ${Object.keys(this.sockets).length} wss ${this.wss.clients.size}`);
  }

  private handleClientMessage = (msg: IMessage, client: Client) => {
    logger.info(`handleClientMessage id ${msg.fromId} to ${msg.toId} messageType ${msg.messageType}`);
    if (msg.channelType === ChannelType.GROUP) {
      if (msg.messageType === MessageType.CONNECTION) {
        this.handleConnectionMessage(msg);
      } else {
        this.sendMessageToGroup(msg);
      }
    } else {
      this.sendMessageToUser(msg);
    }
  }

  private registerClient(this: Server, socket: WebSocket, id: numberOrString,
                         key: string, deviceId: string, user: any) {
    let client = this.clients[id];
    if (!client) {
      client = new Client(id, user, this);
      client.addListener("message", this.handleClientMessage);
      client.addListener("unregister", this.handleClientUnregister);
      this.clients[id] = client;
    }

    client.registerSocket(socket, key, deviceId);
    this.sockets[id] = socket;

    // tslint:disable-next-line:max-line-length
    logger.info(`REGISTERED id ${client.id} clients ${Object.keys(this.clients).length} readyState ${socket.readyState} ` +
                ` sockets ${Object.keys(this.sockets).length} wss ${this.wss.clients.size}`);
  }

  private getConnectionFromHeaders(headers, log: boolean = false): IConnection {
    let protocols = headers["sec-websocket-protocol"];
    if (protocols) { protocols = protocols.split(",").map((entry) => entry.trim()).filter((entry) => entry); }
    const token0 = protocols ? protocols[0] : null;
    const deviceId0  = protocols ? protocols[1] : null;
    const token = headers.token || headers.voicepingtoken || token0;
    const deviceId = headers.device_id || headers.deviceid || deviceId0 || token;
    const connection = { token, deviceId, key: headers["sec-websocket-key"] };
    return connection;
  }

  private getUserFromToken(token) {
    const deferred = Q.defer();
    try {
      const user = jwt.decode(token, config.auth.routerJwtSecret);
      if (user && user.exp && Date.now() / 1000 > user.exp) {
        throw new Error("Token expired");
      }
      deferred.resolve(user);
    } catch (err) {
      if (config.auth.legacyJoinEnabled) {
        deferred.resolve({ uid: token, legacy: true });
      } else {
        deferred.reject(err);
      }
    }
    return deferred.promise;
  }

  /**
   * Websocket client verification
   *
   * @param { object } info
   * @param { function } verified
   * @private
   *
   */
  private verifyClient(this: Server, info, verified) {
    const connection = this.getConnectionFromHeaders(info.req.headers, true);
    const token = connection.token;
    if (!token) { return verified(false, 401, "Unauthorized"); }
    this.getUserFromToken(token)
      .then((user) => {
        return verified(user, 200, "Authorized");
      }).catch((err) => {
        logger.error(`verifyClient getUserFromToken ERR ${err}`);
        return verified(false, 401, "Unauthorized User");
      });
  }

  private handleWssConnection(this: Server, ws: WebSocket, req) {
    const connection = this.getConnectionFromHeaders(req.headers);
    const token = connection.token;
    if (!token) { return; }

    // If deviceId exists on redis, send duplicate login.
    this.getUserFromToken(token)
      .then((user) => {
        logger.info(`handleWssConnection after getUserFromToken. user: ${JSON.stringify(user)}`);
        const deviceId = connection.deviceId;
        const userId = user.userId || user.uid;
        const key = connection.key;

        this.registerClient(ws, userId, key, deviceId, user);
        if (user.channelIds && user.channelIds instanceof Array) {
          user.channelIds.forEach((channelId) => {
            States.addUserToGroup(userId, channelId);
            Redis.addUserToGroup(userId, channelId);
          });
        }
        Redis.getGroupsOfUser(userId, (err, groupIds) => {
          if (groupIds && groupIds instanceof Array) {
            groupIds.forEach((groupId) => {
              States.addUserToGroup(userId, groupId);
            });
          }
        });
      }).catch((err) => {
        logger.error(`handleWssConnection getUserFromToken ERR ${err}`);
      });

  }

  /**
   * Direct message to a destination
   *
   * @param { data } data
   * @param { number } userId
   * @private
   *
   */
  private sendDataToUser(this: Server, data: Buffer, userId: numberOrString) {
    if (this.clients.hasOwnProperty(userId)) {
      const client = this.clients[userId];
      client.send(data);
    } else {
      // debug(`sendDataToUser NOT-FOUND id ${userId}`);
    }
  }

  /**
   * Broadcast a message / data to a channel
   *
   * @param { data } data
   * @param { number } userId
   * @param { number } groupId
   * @private
   *
   */
  private sendDataToRecipients(
    this: Server,
    data: Buffer,
    senderId: numberOrString,
    recipientIds: Array<numberOrString>,
    echo: boolean = false
  ) {
    if (!recipientIds || !(recipientIds instanceof Array) || recipientIds.length <= 0) {
      logger.info(`sendDataToRecipients EMPTY sender ${senderId}`);
      return;
    }
    for (const recipientId of recipientIds) {
      if (!echo && recipientId.toString() === senderId.toString()) { continue; }
      this.sendDataToUser(data, recipientId);
    }
  }

  private broadcastToGroupWithCheck(this: Server, msg: IMessage, userIds: Array<numberOrString>) {
    const senderInGroup = userIds
      ? userIds.map((u) => u.toString()).includes(msg.fromId.toString())
      : false;
    if (!senderInGroup) {
      this.sendMessageToUser({
        channelType: ChannelType.GROUP,
        fromId: msg.fromId,
        messageType: MessageType.UNAUTHORIZED_GROUP,
        payload: "Unauthorized Group",
        toId: msg.fromId
      });
      return;
    }
    packer.pack(msg, (err, packed) => {
      this.sendDataToRecipients(packed, msg.fromId, userIds);
    });
  }

  /**
   *
   * Connection message handler
   *
   * @param { number } userId
   * @param { object } ws
   * @param { data } payload
   * @private
   *
   */
  private handleConnectionMessage = (msg: IMessage): void => {
    logger.info(`handleConnectionMessage id ${msg.fromId} payload ${msg.payload}`);
    /* Buffer should be device token for voip push notification
       device token needs to be either registered or unregistered
       when a new device is connected */
    if (msg.payload) {
      const deviceToken = msg.payload.toString();
      Redis.getGroupsOfUser(msg.fromId, (err, groupIds) => {
        logger.info(`Redis.getGroupsOfUser id ${msg.fromId} groupIds ${groupIds}`);
      });
    }
  }
}

module.exports = Server;
