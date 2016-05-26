/*
 * Copyright 2016-present Facebook, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License. You may obtain
 * a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package com.facebook.buck.macho;

import com.google.common.base.Preconditions;
import com.google.common.primitives.UnsignedInteger;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.UUID;

public class UUIDCommandUtils {
  private UUIDCommandUtils() {}

  public static UUIDCommand createFromBuffer(ByteBuffer buffer) {
    LoadCommand loadCommand = LoadCommand.of(
        buffer.position(),
        UnsignedInteger.fromIntBits(buffer.getInt()),
        UnsignedInteger.fromIntBits(buffer.getInt()));

    ByteOrder order = buffer.order();
    buffer.order(ByteOrder.BIG_ENDIAN);
    long high = buffer.getLong();
    long low = buffer.getLong();
    buffer.order(order);
    UUID uuid = new UUID(high, low);

    return UUIDCommand.of(loadCommand, uuid);
  }

  public static void writeCommandToBuffer(UUIDCommand command, ByteBuffer buffer) {
    LoadCommandUtils.writeCommandToBuffer(command.getLoadCommand(), buffer);

    ByteBuffer uuidBuffer = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN);
    uuidBuffer
        .putLong(command.getUuid().getMostSignificantBits())
        .putLong(command.getUuid().getLeastSignificantBits());
    uuidBuffer.position(0);
    buffer.put(uuidBuffer);
  }

  public static void updateUuidCommand(ByteBuffer buffer, UUIDCommand old, UUIDCommand updated)
      throws IOException {
    Preconditions.checkArgument(
        old.getLoadCommand().getOffsetInBinary() ==
            updated.getLoadCommand().getOffsetInBinary());
    buffer.position(updated.getLoadCommand().getOffsetInBinary());
    writeCommandToBuffer(updated, buffer);
  }
}
