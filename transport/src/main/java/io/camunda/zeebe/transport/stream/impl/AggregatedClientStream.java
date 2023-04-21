/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.transport.stream.impl;

import io.atomix.cluster.MemberId;
import io.camunda.zeebe.transport.stream.api.ClientStreamConsumer;
import io.camunda.zeebe.util.buffer.BufferWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.agrona.DirectBuffer;
import org.agrona.collections.Int2ObjectHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Represents a stream which aggregates multiple logically equivalent client streams. * */
final class AggregatedClientStream<M extends BufferWriter> {

  private static final Logger LOGGER = LoggerFactory.getLogger(AggregatedClientStream.class);
  private final UUID streamId;
  private final LogicalId<M> logicalId;
  private final ClientStreamConsumer streamConsumer;
  private final Set<MemberId> liveConnections = new HashSet<>();

  private final Int2ObjectHashMap<ClientStream<M>> clientStreams = new Int2ObjectHashMap<>();

  private State state;
  private int nextLocalId;

  AggregatedClientStream(final UUID streamId, final LogicalId<M> logicalId) {
    this.streamId = streamId;
    this.logicalId = logicalId;

    streamConsumer = this::push;
    state = State.INITIAL;
  }

  void addClient(final ClientStream<M> clientStream) {
    clientStreams.put(clientStream.streamId().localId(), clientStream);
  }

  UUID getStreamId() {
    return streamId;
  }

  DirectBuffer getStreamType() {
    return logicalId.streamType();
  }

  M getMetadata() {
    return logicalId.metadata();
  }

  ClientStreamConsumer getClientStreamConsumer() {
    return streamConsumer;
  }

  int nextLocalId() {
    final var localId = nextLocalId;
    nextLocalId++;
    return localId;
  }

  /**
   * Mark that this stream is registered with the given server. Server can send data to this stream
   * from now on.
   *
   * @param serverId id of the server
   */
  void add(final MemberId serverId) {
    liveConnections.add(serverId);
  }

  /**
   * If true, the stream is registered with the given server. If false, it is also possible the
   * stream is registered with the server, but we failed to receive the acknowledgement.
   *
   * @param serverId id of the server
   * @return true if a server has acknowledged to add stream request
   */
  boolean isConnected(final MemberId serverId) {
    return liveConnections.contains(serverId);
  }

  /**
   * Mark that stream to this server is closed.
   *
   * @param serverId id of the server
   */
  void remove(final MemberId serverId) {
    liveConnections.remove(serverId);
  }

  void close() {
    state = State.CLOSED;
  }

  boolean isClosed() {
    return state == State.CLOSED;
  }

  void removeClient(final ClientStreamIdImpl streamId) {
    clientStreams.remove(streamId.localId());
  }

  /** returns true if there are no client streams for this stream * */
  boolean isEmpty() {
    return clientStreams.isEmpty();
  }

  LogicalId<M> logicalId() {
    return logicalId;
  }

  private void push(final DirectBuffer buffer) {
    final var streams = clientStreams.values();
    if (streams.isEmpty()) {
      throw new NoSuchStreamException();
    }

    final ClientStream<M> clientStream = pickRandomStream(streams);
    LOGGER.trace("Pushing data from stream [{}] to client [{}]", streamId, clientStream.streamId());
    clientStream.clientStreamConsumer().push(buffer);
  }

  private ClientStream<M> pickRandomStream(final Collection<ClientStream<M>> streams) {
    final var targets = new ArrayList<>(streams);
    final var index = ThreadLocalRandom.current().nextInt(streams.size());

    return targets.get(index);
  }

  void open(final ClientStreamRequestManager<M> requestManager, final Set<MemberId> servers) {
    if (state == State.INITIAL) {
      requestManager.openStream(this, servers);
      state = State.OPEN;
    }
  }

  record LogicalId<M>(DirectBuffer streamType, M metadata) {}

  private enum State {
    INITIAL,
    OPEN,
    CLOSED
  }
}