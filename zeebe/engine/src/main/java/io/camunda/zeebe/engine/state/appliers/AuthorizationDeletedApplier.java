/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.state.appliers;

import io.camunda.zeebe.engine.state.TypedEventApplier;
import io.camunda.zeebe.engine.state.mutable.MutableAuthorizationState;
import io.camunda.zeebe.protocol.impl.record.value.authorization.AuthorizationRecord;
import io.camunda.zeebe.protocol.record.intent.AuthorizationIntent;

public class AuthorizationDeletedApplier
    implements TypedEventApplier<AuthorizationIntent, AuthorizationRecord> {

  private final MutableAuthorizationState authorizationState;

  public AuthorizationDeletedApplier(final MutableAuthorizationState state) {
    authorizationState = state;
  }

  @Override
  public void applyState(final long key, final AuthorizationRecord value) {
    authorizationState.delete(key);
  }
}
