/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.webapps.operate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

final class TreePathTest {
  @Nested
  final class ProcessInstanceIdForFniIdTest {
    @Test
    void shouldFindPidForFniId() {
      // given
      final var treePath = new TreePath("PI_1/FN_1/FNI_1/PI_2/FN_3/FNI_3");

      // when
      final var piId = treePath.processInstanceForFni("3");

      // then
      assertThat(piId).hasValue("2");
    }

    @Test
    void shouldNotFindPidForNonExistentFni() {
      // given
      final var treePath = new TreePath("PI_1/FN_1/FNI_1");

      // when
      final var piId = treePath.processInstanceForFni("2");

      // then
      assertThat(piId).isEmpty();
    }

    @Test
    void shouldNotFailOnEmptyPath() {
      // given
      final var treePath = new TreePath("");

      // when
      final var piId = treePath.processInstanceForFni("2");

      // then
      assertThat(piId).isEmpty();
    }

    @Test
    void shouldFindPiStartingFromMiddle() {
      // given
      final var treePath = new TreePath("PI_1/FN_1/FNI_1/PI_2/FN_3/FNI_3");

      // when
      final var piId = treePath.processInstanceForFni("1");

      // then
      assertThat(piId).hasValue("1");
    }
  }
}
