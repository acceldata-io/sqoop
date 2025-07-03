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

package org.apache.sqoop.mapreduce.hcat;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.hive.hcatalog.common.HCatConstants;
import org.apache.hive.hcatalog.common.HCatUtil;
import org.apache.hive.hcatalog.data.schema.HCatFieldSchema;
import org.apache.hive.hcatalog.data.schema.HCatSchema;
import org.apache.hive.hcatalog.mapreduce.InputJobInfo;
import org.apache.hive.hcatalog.mapreduce.StorerInfo;
import org.apache.sqoop.testcategories.sqooptest.UnitTest;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@Category(UnitTest.class)
public class TestInputJobInfoSelector {

  private Configuration conf;
  private InputJobInfo mockJobInfo1;
  private InputJobInfo mockJobInfo2;

  @Before
  public void setUp() throws Exception {
    conf = new Configuration();
    
    // Create mock InputJobInfo objects
    mockJobInfo1 = createMockInputJobInfo("table1", 3, true);
    mockJobInfo2 = createMockInputJobInfo("table2", 5, true);
  }

  @Test
  public void testSingleInputJobInfoDirect() throws Exception {
    String serialized = HCatUtil.serialize(mockJobInfo1);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertEquals(mockJobInfo1, result.getSelectedJobInfo());
    assertEquals(InputJobInfoSelector.SelectionStrategy.DIRECT_CAST, result.getUsedStrategy());
    assertEquals(1, result.getTotalCandidates());
    assertEquals(1, result.getValidCandidates());
  }

  @Test
  public void testSingleInputJobInfoInList() throws Exception {
    List<InputJobInfo> jobInfoList = Arrays.asList(mockJobInfo1);
    String serialized = HCatUtil.serialize(jobInfoList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertEquals(mockJobInfo1, result.getSelectedJobInfo());
    assertEquals(InputJobInfoSelector.SelectionStrategy.FIRST_VALID, result.getUsedStrategy());
    assertEquals(1, result.getTotalCandidates());
    assertEquals(1, result.getValidCandidates());
  }

  @Test
  public void testMultipleInputJobInfoInList() throws Exception {
    List<InputJobInfo> jobInfoList = Arrays.asList(mockJobInfo1, mockJobInfo2);
    String serialized = HCatUtil.serialize(jobInfoList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertNotNull(result.getSelectedJobInfo());
    assertTrue(result.getUsedStrategy() == InputJobInfoSelector.SelectionStrategy.MOST_COMPLETE ||
               result.getUsedStrategy() == InputJobInfoSelector.SelectionStrategy.FIRST_VALID);
    assertEquals(2, result.getTotalCandidates());
    assertEquals(2, result.getValidCandidates());
  }

  @Test
  public void testMultipleInputJobInfoStrictMode() throws Exception {
    conf.setBoolean(InputJobInfoSelector.HCAT_INPUTJOBINFO_SELECTION_STRICT, true);
    
    List<InputJobInfo> jobInfoList = Arrays.asList(mockJobInfo1, mockJobInfo2);
    String serialized = HCatUtil.serialize(jobInfoList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    try {
      InputJobInfoSelector.selectInputJobInfo(conf);
      fail("Expected IOException in strict mode with multiple InputJobInfo objects");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("strict mode"));
    }
  }

  @Test
  public void testEmptyList() throws Exception {
    List<InputJobInfo> emptyList = new ArrayList<>();
    String serialized = HCatUtil.serialize(emptyList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    try {
      InputJobInfoSelector.selectInputJobInfo(conf);
      fail("Expected IOException for empty list");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("empty List"));
    }
  }

  @Test
  public void testListWithNoInputJobInfo() throws Exception {
    List<String> stringList = Arrays.asList("not", "an", "inputjobinfo");
    String serialized = HCatUtil.serialize(stringList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    try {
      InputJobInfoSelector.selectInputJobInfo(conf);
      fail("Expected IOException for list with no InputJobInfo objects");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("no InputJobInfo objects"));
    }
  }

  @Test
  public void testMixedList() throws Exception {
    List<Object> mixedList = Arrays.asList(mockJobInfo1, "string", mockJobInfo2, null);
    String serialized = HCatUtil.serialize(mixedList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertNotNull(result.getSelectedJobInfo());
    assertEquals(4, result.getTotalCandidates());
    assertEquals(2, result.getValidCandidates());
  }

  @Test
  public void testNullConfiguration() throws Exception {
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, (String) null);

    try {
      InputJobInfoSelector.selectInputJobInfo(conf);
      fail("Expected IOException for null job info string");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("null or empty"));
    }
  }

  @Test
  public void testEmptyConfiguration() throws Exception {
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, "");

    try {
      InputJobInfoSelector.selectInputJobInfo(conf);
      fail("Expected IOException for empty job info string");
    } catch (IOException e) {
      assertTrue(e.getMessage().contains("null or empty"));
    }
  }

  @Test
  public void testMostCompleteSelection() throws Exception {
    // Create a more complete job info (with more metadata)
    InputJobInfo lessComplete = createMockInputJobInfo("table1", 2, false);
    InputJobInfo moreComplete = createMockInputJobInfo("table2", 5, true);
    
    List<InputJobInfo> jobInfoList = Arrays.asList(lessComplete, moreComplete);
    String serialized = HCatUtil.serialize(jobInfoList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertEquals(moreComplete, result.getSelectedJobInfo());
    assertEquals(InputJobInfoSelector.SelectionStrategy.MOST_COMPLETE, result.getUsedStrategy());
  }

  @Test
  public void testInvalidInputJobInfoHandling() throws Exception {
    // Create an invalid InputJobInfo (null table info)
    InputJobInfo invalidJobInfo = mock(InputJobInfo.class);
    when(invalidJobInfo.getTableInfo()).thenReturn(null);
    
    List<InputJobInfo> jobInfoList = Arrays.asList(invalidJobInfo, mockJobInfo1);
    String serialized = HCatUtil.serialize(jobInfoList);
    conf.set(HCatConstants.HCAT_KEY_JOB_INFO, serialized);

    InputJobInfoSelector.SelectionResult result = InputJobInfoSelector.selectInputJobInfo(conf);

    assertNotNull(result);
    assertEquals(mockJobInfo1, result.getSelectedJobInfo());
    assertEquals(2, result.getTotalCandidates());
    assertEquals(1, result.getValidCandidates()); // Only one valid candidate
  }

  /**
   * Helper method to create a mock InputJobInfo with specified characteristics.
   */
  private InputJobInfo createMockInputJobInfo(String tableName, int columnCount, boolean withStorerInfo) 
          throws Exception {
    InputJobInfo jobInfo = mock(InputJobInfo.class);
    InputJobInfo.TableInfo tableInfo = mock(InputJobInfo.TableInfo.class);
    
    // Create mock schema with specified number of columns
    List<HCatFieldSchema> fields = new ArrayList<>();
    for (int i = 0; i < columnCount; i++) {
      HCatFieldSchema field = new HCatFieldSchema("col" + i, HCatFieldSchema.Type.STRING, "");
      fields.add(field);
    }
    HCatSchema dataSchema = new HCatSchema(fields);
    HCatSchema partitionSchema = new HCatSchema(new ArrayList<HCatFieldSchema>());
    
    when(tableInfo.getDataColumns()).thenReturn(dataSchema);
    when(tableInfo.getPartitionColumns()).thenReturn(partitionSchema);
    when(tableInfo.getTableLocation()).thenReturn("/path/to/" + tableName);
    
    if (withStorerInfo) {
      StorerInfo storerInfo = mock(StorerInfo.class);
      when(storerInfo.getStorageHandlerClass()).thenReturn("TestStorageHandler");
      when(tableInfo.getStorerInfo()).thenReturn(storerInfo);
    } else {
      when(tableInfo.getStorerInfo()).thenReturn(null);
    }
    
    when(jobInfo.getTableInfo()).thenReturn(tableInfo);
    
    return jobInfo;
  }
}