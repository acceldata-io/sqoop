/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.sqoop.util;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Utility helpers around protobuf runtime compatibility checks.
 */
public final class ProtobufCompat {

  private static final Log LOG = LogFactory.getLog(ProtobufCompat.class.getName());

  private static final boolean HAS_LEGACY_GENERATED_MESSAGE_ADD_ALL = detectLegacyGeneratedMessageAddAll();

  private ProtobufCompat() {
  }

  /**
   * @return true when {@code com.google.protobuf.GeneratedMessage$Builder} still exposes
   * the legacy {@code addAll(Iterable, List)} helper that older Hive protobuf logging hook
   * classes were compiled against.
   */
  public static boolean supportsLegacyGeneratedMessageAddAll() {
    return HAS_LEGACY_GENERATED_MESSAGE_ADD_ALL;
  }

  private static boolean detectLegacyGeneratedMessageAddAll() {
    try {
      Class<?> builderClass = Class.forName("com.google.protobuf.GeneratedMessage$Builder");
      builderClass.getMethod("addAll", Iterable.class, List.class);
      return true;
    } catch (ClassNotFoundException e) {
      if (LOG.isDebugEnabled()) {
        LOG.debug("com.google.protobuf.GeneratedMessage$Builder not found; assuming legacy "
            + "addAll support is available.", e);
      }
      return true;
    } catch (NoSuchMethodException e) {
      LOG.info("Detected protobuf runtime missing "
          + "GeneratedMessage$Builder.addAll(Iterable,List); Hive proto logging hooks "
          + "compiled against older protobuf will be incompatible.");
      return false;
    } catch (LinkageError | SecurityException e) {
      LOG.warn("Unable to inspect protobuf runtime for "
          + "GeneratedMessage$Builder.addAll(Iterable,List); assuming incompatibility.", e);
      return false;
    }
  }
}
