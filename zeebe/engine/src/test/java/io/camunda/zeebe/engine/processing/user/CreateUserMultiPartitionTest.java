/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Camunda License 1.0. You may not use this file
 * except in compliance with the Camunda License 1.0.
 */
package io.camunda.zeebe.engine.processing.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.AssertionsForClassTypes.tuple;

import io.camunda.zeebe.engine.util.EngineRule;
import io.camunda.zeebe.protocol.record.Record;
import io.camunda.zeebe.protocol.record.RecordType;
import io.camunda.zeebe.protocol.record.ValueType;
import io.camunda.zeebe.protocol.record.intent.CommandDistributionIntent;
import io.camunda.zeebe.protocol.record.intent.UserIntent;
import io.camunda.zeebe.protocol.record.value.CommandDistributionRecordValue;
import io.camunda.zeebe.test.util.record.RecordingExporter;
import io.camunda.zeebe.test.util.record.RecordingExporterTestWatcher;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestWatcher;

public class CreateUserMultiPartitionTest {
  private static final int PARTITION_COUNT = 3;

  @ClassRule public static final EngineRule ENGINE = EngineRule.multiplePartition(PARTITION_COUNT);

  @Rule public final TestWatcher testWatcher = new RecordingExporterTestWatcher();

  @Test
  public void shouldDistributeUserCreateCommand() {
    // when
    final var username = UUID.randomUUID().toString();

    ENGINE
        .user()
        .newUser(username)
        .withName("Foo Bar")
        .withEmail("foo@bar.com")
        .withPassword("password")
        .create();

    assertThat(
            RecordingExporter.records()
                .withPartitionId(1)
                .limit(record -> record.getIntent().equals(CommandDistributionIntent.FINISHED))
                .filter(
                    record ->
                        record.getValueType() == ValueType.USER
                            || (record.getValueType() == ValueType.COMMAND_DISTRIBUTION
                                && ((CommandDistributionRecordValue) record.getValue()).getIntent()
                                    == UserIntent.CREATE)))
        .extracting(
            Record::getIntent,
            Record::getRecordType,
            r ->
                // We want to verify the partition id where the creation was distributing to and
                // where it was completed. Since only the CommandDistribution records have a
                // value that contains the partition id, we use the partition id the record was
                // written on for the other records.
                r.getValue() instanceof CommandDistributionRecordValue
                    ? ((CommandDistributionRecordValue) r.getValue()).getPartitionId()
                    : r.getPartitionId())
        .startsWith(
            tuple(UserIntent.CREATE, RecordType.COMMAND, 1),
            tuple(UserIntent.CREATED, RecordType.EVENT, 1),
            tuple(CommandDistributionIntent.STARTED, RecordType.EVENT, 1))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 2),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 2))
        .containsSubsequence(
            tuple(CommandDistributionIntent.DISTRIBUTING, RecordType.EVENT, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGE, RecordType.COMMAND, 3),
            tuple(CommandDistributionIntent.ACKNOWLEDGED, RecordType.EVENT, 3))
        .endsWith(tuple(CommandDistributionIntent.FINISHED, RecordType.EVENT, 1));
    for (int partitionId = 2; partitionId < PARTITION_COUNT; partitionId++) {
      assertThat(
              RecordingExporter.userRecords()
                  .withUsername(username)
                  .withPartitionId(partitionId)
                  .limit(record -> record.getIntent().equals(UserIntent.CREATED))
                  .collect(Collectors.toList()))
          .extracting(Record::getIntent)
          .containsExactly(UserIntent.CREATE, UserIntent.CREATED);
    }
  }
}
