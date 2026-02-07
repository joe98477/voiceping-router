/**
 * mediasoup Producer/Consumer Manager
 * Manages audio producer and consumer lifecycle with pause/resume for PTT
 */

import { types as mediasoupTypes } from 'mediasoup';
import { TransportManager } from './transportManager';
import { createLogger } from '../logger';

const logger = createLogger('ProducerConsumerManager');

/**
 * Producer metadata for tracking
 */
interface ProducerMetadata {
  producer: mediasoupTypes.Producer;
  userId: string;
  channelId: string;
}

/**
 * Consumer metadata for tracking
 */
interface ConsumerMetadata {
  consumer: mediasoupTypes.Consumer;
  userId: string;
}

/**
 * ProducerConsumerManager handles audio stream lifecycle
 * Producers send audio (PTT transmit), consumers receive audio
 */
export class ProducerConsumerManager {
  private producers = new Map<string, ProducerMetadata>();
  private consumers = new Map<string, ConsumerMetadata>();
  private transportManager: TransportManager;

  constructor(transportManager: TransportManager) {
    this.transportManager = transportManager;
  }

  /**
   * Create audio producer on transport (PTT transmit)
   */
  async createProducer(
    transportId: string,
    kind: mediasoupTypes.MediaKind,
    rtpParameters: mediasoupTypes.RtpParameters,
    userId: string,
    channelId: string
  ): Promise<string> {
    const transport = this.transportManager.getTransport(transportId);

    if (!transport) {
      throw new Error(`Transport ${transportId} not found`);
    }

    const producer = await transport.produce({
      kind,
      rtpParameters,
      paused: true, // PTT starts paused (not transmitting)
    });

    this.producers.set(producer.id, {
      producer,
      userId,
      channelId,
    });

    // Handle transport close
    producer.on('transportclose', () => {
      logger.info(`Producer ${producer.id} transport closed, cleaning up`);
      this.producers.delete(producer.id);
    });

    logger.info(`Created producer ${producer.id} for user ${userId} in channel ${channelId}`);

    return producer.id;
  }

  /**
   * Create audio consumer on transport (PTT receive)
   */
  async createConsumer(
    transportId: string,
    producerId: string,
    rtpCapabilities: mediasoupTypes.RtpCapabilities,
    userId: string,
    channelId: string
  ): Promise<{
    id: string;
    producerId: string;
    kind: mediasoupTypes.MediaKind;
    rtpParameters: mediasoupTypes.RtpParameters;
  }> {
    const transport = this.transportManager.getTransport(transportId);

    if (!transport) {
      throw new Error(`Transport ${transportId} not found`);
    }

    const producerMetadata = this.producers.get(producerId);

    if (!producerMetadata) {
      throw new Error(`Producer ${producerId} not found`);
    }

    // Get router for the channel where the producer exists
    const router = this.transportManager.getRouterForChannel(producerMetadata.channelId);

    if (!router) {
      throw new Error(`Router for channel ${producerMetadata.channelId} not found`);
    }

    if (!router.canConsume({ producerId, rtpCapabilities })) {
      throw new Error(`Cannot consume producer ${producerId} with provided RTP capabilities`);
    }

    const consumer = await transport.consume({
      producerId,
      rtpCapabilities,
      paused: true, // Consumer starts paused, client resumes after setup
    });

    this.consumers.set(consumer.id, {
      consumer,
      userId,
    });

    // Handle transport close
    consumer.on('transportclose', () => {
      logger.info(`Consumer ${consumer.id} transport closed, cleaning up`);
      this.consumers.delete(consumer.id);
    });

    // Handle producer close
    consumer.on('producerclose', () => {
      logger.info(`Consumer ${consumer.id} producer closed, cleaning up`);
      this.consumers.delete(consumer.id);
    });

    logger.info(`Created consumer ${consumer.id} for user ${userId} consuming producer ${producerId}`);

    return {
      id: consumer.id,
      producerId: consumer.producerId,
      kind: consumer.kind,
      rtpParameters: consumer.rtpParameters,
    };
  }

  /**
   * Resume producer (PTT start transmitting)
   */
  async resumeProducer(producerId: string): Promise<void> {
    const metadata = this.producers.get(producerId);

    if (!metadata) {
      throw new Error(`Producer ${producerId} not found`);
    }

    if (!metadata.producer.paused) {
      logger.warn(`Producer ${producerId} already resumed`);
      return;
    }

    await metadata.producer.resume();
    logger.info(`Producer ${producerId} resumed (PTT transmitting)`);
  }

  /**
   * Pause producer (PTT stop transmitting)
   */
  async pauseProducer(producerId: string): Promise<void> {
    const metadata = this.producers.get(producerId);

    if (!metadata) {
      throw new Error(`Producer ${producerId} not found`);
    }

    if (metadata.producer.paused) {
      logger.warn(`Producer ${producerId} already paused`);
      return;
    }

    await metadata.producer.pause();
    logger.info(`Producer ${producerId} paused (PTT stopped)`);
  }

  /**
   * Resume consumer (start receiving audio)
   */
  async resumeConsumer(consumerId: string): Promise<void> {
    const metadata = this.consumers.get(consumerId);

    if (!metadata) {
      throw new Error(`Consumer ${consumerId} not found`);
    }

    if (!metadata.consumer.paused) {
      logger.warn(`Consumer ${consumerId} already resumed`);
      return;
    }

    await metadata.consumer.resume();
    logger.info(`Consumer ${consumerId} resumed (receiving audio)`);
  }

  /**
   * Get all active producers for a channel
   */
  getProducersForChannel(channelId: string): ProducerMetadata[] {
    const producers: ProducerMetadata[] = [];

    for (const metadata of this.producers.values()) {
      if (metadata.channelId === channelId) {
        producers.push(metadata);
      }
    }

    return producers;
  }

  /**
   * Close and remove producer
   */
  async closeProducer(producerId: string): Promise<void> {
    const metadata = this.producers.get(producerId);

    if (!metadata) {
      logger.warn(`Producer ${producerId} not found for closing`);
      return;
    }

    metadata.producer.close();
    this.producers.delete(producerId);
    logger.info(`Producer ${producerId} closed`);
  }

  /**
   * Close and remove consumer
   */
  async closeConsumer(consumerId: string): Promise<void> {
    const metadata = this.consumers.get(consumerId);

    if (!metadata) {
      logger.warn(`Consumer ${consumerId} not found for closing`);
      return;
    }

    metadata.consumer.close();
    this.consumers.delete(consumerId);
    logger.info(`Consumer ${consumerId} closed`);
  }

  /**
   * Close producers and consumers for a user in a specific channel
   */
  async closeUserChannelProducersAndConsumers(userId: string, channelId: string): Promise<void> {
    const producersToClose: string[] = [];
    const consumersToClose: string[] = [];

    for (const [id, metadata] of this.producers.entries()) {
      if (metadata.userId === userId && metadata.channelId === channelId) {
        producersToClose.push(id);
      }
    }

    for (const [id, metadata] of this.consumers.entries()) {
      if (metadata.userId === userId) {
        consumersToClose.push(id);
      }
    }

    for (const id of producersToClose) {
      await this.closeProducer(id);
    }

    for (const id of consumersToClose) {
      await this.closeConsumer(id);
    }

    if (producersToClose.length > 0 || consumersToClose.length > 0) {
      logger.info(
        `Closed ${producersToClose.length} producers and ${consumersToClose.length} consumers for user ${userId} in channel ${channelId}`
      );
    }
  }

  /**
   * Close all producers and consumers for a user (cleanup on disconnect)
   */
  async closeUserProducersAndConsumers(userId: string): Promise<void> {
    const producersToClose: string[] = [];
    const consumersToClose: string[] = [];

    for (const [id, metadata] of this.producers.entries()) {
      if (metadata.userId === userId) {
        producersToClose.push(id);
      }
    }

    for (const [id, metadata] of this.consumers.entries()) {
      if (metadata.userId === userId) {
        consumersToClose.push(id);
      }
    }

    for (const id of producersToClose) {
      await this.closeProducer(id);
    }

    for (const id of consumersToClose) {
      await this.closeConsumer(id);
    }

    if (producersToClose.length > 0 || consumersToClose.length > 0) {
      logger.info(
        `Closed ${producersToClose.length} producers and ${consumersToClose.length} consumers for user ${userId}`
      );
    }
  }
}
