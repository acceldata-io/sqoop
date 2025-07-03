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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.hadoop.conf.Configuration;
import org.apache.hive.hcatalog.common.HCatConstants;
import org.apache.hive.hcatalog.common.HCatUtil;
import org.apache.hive.hcatalog.mapreduce.InputJobInfo;

/**
 * Utility class for robust selection of InputJobInfo objects from
 * HCatalog deserialization results. Handles various scenarios including
 * multiple InputJobInfo objects, empty lists, and serialization compatibility issues.
 */
public class InputJobInfoSelector {
  
  public static final Log LOG = LogFactory.getLog(InputJobInfoSelector.class.getName());
  
  // Configuration property for controlling selection behavior
  public static final String HCAT_INPUTJOBINFO_SELECTION_STRICT = "sqoop.hcat.inputjobinfo.selection.strict";
  public static final boolean HCAT_INPUTJOBINFO_SELECTION_STRICT_DEFAULT = false;
  
  /**
   * Selection result containing the chosen InputJobInfo and metadata about the selection process.
   */
  public static class SelectionResult {
    private final InputJobInfo selectedJobInfo;
    private final SelectionStrategy usedStrategy;
    private final String selectionReason;
    private final int totalCandidates;
    private final int validCandidates;
    
    public SelectionResult(InputJobInfo selectedJobInfo, SelectionStrategy usedStrategy, 
                          String selectionReason, int totalCandidates, int validCandidates) {
      this.selectedJobInfo = selectedJobInfo;
      this.usedStrategy = usedStrategy;
      this.selectionReason = selectionReason;
      this.totalCandidates = totalCandidates;
      this.validCandidates = validCandidates;
    }
    
    public InputJobInfo getSelectedJobInfo() {
      return selectedJobInfo;
    }
    
    public SelectionStrategy getUsedStrategy() {
      return usedStrategy;
    }
    
    public String getSelectionReason() {
      return selectionReason;
    }
    
    public int getTotalCandidates() {
      return totalCandidates;
    }
    
    public int getValidCandidates() {
      return validCandidates;
    }
  }
  
  /**
   * Enumeration of selection strategies used to choose InputJobInfo.
   */
  public enum SelectionStrategy {
    DIRECT_CAST,           // Single InputJobInfo object
    FIRST_VALID,           // First valid object from list
    MOST_COMPLETE,         // Most complete object based on metadata
    SCHEMA_MATCH,          // Best schema compatibility
    DEFAULT_FALLBACK       // Last resort selection
  }
  
  /**
   * Selects an appropriate InputJobInfo from HCatalog deserialization results.
   * 
   * @param conf Hadoop configuration
   * @return SelectionResult containing the chosen InputJobInfo and selection metadata
   * @throws IOException if no valid InputJobInfo can be found
   */
  public static SelectionResult selectInputJobInfo(Configuration conf) throws IOException {
    String inputJobInfoStr = conf.get(HCatConstants.HCAT_KEY_JOB_INFO);
    if (inputJobInfoStr == null || inputJobInfoStr.trim().isEmpty()) {
      throw new IOException("HCatalog job info string is null or empty. " +
              "Ensure that HCatalog integration is properly configured.");
    }
    
    Object deserializedObj;
    try {
      deserializedObj = HCatUtil.deserialize(inputJobInfoStr);
    } catch (Exception e) {
      throw new IOException("Failed to deserialize HCatalog job info: " + e.getMessage(), e);
    }
    
    if (deserializedObj == null) {
      throw new IOException("HCatalog deserialization returned null object. " +
              "This may indicate a serialization compatibility issue.");
    }
    
    // Handle direct InputJobInfo case
    if (deserializedObj instanceof InputJobInfo) {
      InputJobInfo jobInfo = (InputJobInfo) deserializedObj;
      validateInputJobInfo(jobInfo);
      LOG.info("HCatalog deserialization returned single InputJobInfo object directly.");
      return new SelectionResult(jobInfo, SelectionStrategy.DIRECT_CAST, 
                               "Single InputJobInfo object", 1, 1);
    }
    
    // Handle List case
    if (deserializedObj instanceof List) {
      List<?> list = (List<?>) deserializedObj;
      return selectFromList(list, conf);
    }
    
    // Handle unexpected type
    throw new IOException("Failed to deserialize InputJobInfo. Expected InputJobInfo or List but got "
            + deserializedObj.getClass().getName() + ". This may indicate a version compatibility issue " +
            "between HCatalog components.");
  }
  
  /**
   * Selects the best InputJobInfo from a list of candidates.
   */
  private static SelectionResult selectFromList(List<?> list, Configuration conf) throws IOException {
    if (list.isEmpty()) {
      throw new IOException("Failed to deserialize InputJobInfo. Deserialized as empty List. " +
              "This may indicate a serialization issue or missing table metadata.");
    }
    
    boolean strictMode = conf.getBoolean(HCAT_INPUTJOBINFO_SELECTION_STRICT, 
                                        HCAT_INPUTJOBINFO_SELECTION_STRICT_DEFAULT);
    
    // Analyze list contents
    StringBuilder listContents = new StringBuilder("List contents (size=" + list.size() + "): [");
    InputJobInfo[] candidates = new InputJobInfo[list.size()];
    int inputJobInfoCount = 0;
    
    for (int i = 0; i < list.size(); i++) {
      Object element = list.get(i);
      if (i > 0) listContents.append(", ");
      
      if (element instanceof InputJobInfo) {
        candidates[inputJobInfoCount] = (InputJobInfo) element;
        inputJobInfoCount++;
        listContents.append("InputJobInfo@").append(i);
      } else {
        listContents.append(element != null ? element.getClass().getSimpleName() : "null").append("@").append(i);
      }
    }
    listContents.append("]");
    
    LOG.info("HCatalog deserialization returned List with " + list.size() + " elements, " +
             inputJobInfoCount + " of which are InputJobInfo objects. " + listContents.toString());
    
    if (inputJobInfoCount == 0) {
      throw new IOException("Failed to deserialize InputJobInfo. Deserialized as List but contained no " +
              "InputJobInfo objects. " + listContents.toString());
    }
    
    // Trim candidates array to actual size
    InputJobInfo[] validCandidates = new InputJobInfo[inputJobInfoCount];
    System.arraycopy(candidates, 0, validCandidates, 0, inputJobInfoCount);
    
    // Handle single candidate case
    if (inputJobInfoCount == 1) {
      InputJobInfo selected = validCandidates[0];
      validateInputJobInfo(selected);
      
      if (list.size() > 1) {
        LOG.warn("HCatalog deserialization returned List with " + list.size() + " elements but only 1 " +
                "InputJobInfo. Using the InputJobInfo and ignoring other elements. " + listContents.toString());
      }
      
      return new SelectionResult(selected, SelectionStrategy.FIRST_VALID,
                               "Single valid InputJobInfo found in list", list.size(), 1);
    }
    
    // Handle multiple candidates
    return selectBestCandidate(validCandidates, strictMode, list.size());
  }
  
  /**
   * Selects the best candidate from multiple InputJobInfo objects.
   */
  private static SelectionResult selectBestCandidate(InputJobInfo[] candidates, boolean strictMode, int totalElements) 
          throws IOException {
    
    if (strictMode) {
      throw new IOException("HCatalog deserialization returned " + candidates.length + " InputJobInfo objects " +
              "in strict mode. This scenario is not allowed when " + HCAT_INPUTJOBINFO_SELECTION_STRICT + 
              " is enabled. Consider setting this property to false or review your HCatalog configuration.");
    }
    
    LOG.warn("HCatalog deserialization returned " + candidates.length + " InputJobInfo objects. " +
            "This may indicate a serialization compatibility issue or multiple table partitions. " +
            "Attempting to select the most appropriate candidate.");
    
    // Validate all candidates and find the best one
    InputJobInfo bestCandidate = null;
    String bestReason = null;
    SelectionStrategy strategy = SelectionStrategy.DEFAULT_FALLBACK;
    
    // Strategy 1: Find the most complete candidate (most metadata)
    InputJobInfo mostComplete = findMostCompleteCandidate(candidates);
    if (mostComplete != null) {
      bestCandidate = mostComplete;
      bestReason = "Selected candidate with most complete metadata";
      strategy = SelectionStrategy.MOST_COMPLETE;
    }
    
    // Strategy 2: If no clear winner, use the first valid one
    if (bestCandidate == null) {
      for (InputJobInfo candidate : candidates) {
        if (isValidInputJobInfo(candidate)) {
          bestCandidate = candidate;
          bestReason = "Selected first valid candidate as fallback";
          strategy = SelectionStrategy.FIRST_VALID;
          break;
        }
      }
    }
    
    // Final validation
    if (bestCandidate == null) {
      throw new IOException("Failed to find any valid InputJobInfo among " + candidates.length + 
              " candidates. All candidates appear to be incomplete or invalid.");
    }
    
    validateInputJobInfo(bestCandidate);
    
    LOG.warn("Selected InputJobInfo using strategy: " + strategy + ". Reason: " + bestReason + 
            ". If this behavior is unexpected, consider reviewing your HCatalog/Hive configuration.");
    
    return new SelectionResult(bestCandidate, strategy, bestReason, totalElements, candidates.length);
  }
  
  /**
   * Finds the most complete InputJobInfo candidate based on available metadata.
   */
  private static InputJobInfo findMostCompleteCandidate(InputJobInfo[] candidates) {
    InputJobInfo best = null;
    int bestScore = -1;
    
    for (InputJobInfo candidate : candidates) {
      int score = calculateCompletenessScore(candidate);
      if (score > bestScore) {
        bestScore = score;
        best = candidate;
      }
    }
    
    return bestScore > 0 ? best : null;
  }
  
  /**
   * Calculates a completeness score for an InputJobInfo object.
   */
  private static int calculateCompletenessScore(InputJobInfo jobInfo) {
    if (!isValidInputJobInfo(jobInfo)) {
      return -1;
    }
    
    int score = 0;
    
    try {
      // Check table info
      if (jobInfo.getTableInfo() != null) {
        score += 10;
        
        // Check data columns
        if (jobInfo.getTableInfo().getDataColumns() != null && 
            !jobInfo.getTableInfo().getDataColumns().getFields().isEmpty()) {
          score += 20;
        }
        
        // Check partition columns
        if (jobInfo.getTableInfo().getPartitionColumns() != null) {
          score += 10;
        }
        
        // Check storer info
        if (jobInfo.getTableInfo().getStorerInfo() != null) {
          score += 15;
        }
        
        // Check table location
        if (jobInfo.getTableInfo().getTableLocation() != null && 
            !jobInfo.getTableInfo().getTableLocation().trim().isEmpty()) {
          score += 5;
        }
      }
    } catch (Exception e) {
      LOG.debug("Error calculating completeness score for InputJobInfo: " + e.getMessage());
      return -1;
    }
    
    return score;
  }
  
  /**
   * Validates that an InputJobInfo object has the minimum required metadata.
   */
  private static void validateInputJobInfo(InputJobInfo jobInfo) throws IOException {
    if (!isValidInputJobInfo(jobInfo)) {
      throw new IOException("Selected InputJobInfo is invalid or incomplete. " +
              "Table metadata may be missing or corrupted.");
    }
  }
  
  /**
   * Checks if an InputJobInfo object is valid and has the minimum required metadata.
   */
  private static boolean isValidInputJobInfo(InputJobInfo jobInfo) {
    if (jobInfo == null) {
      return false;
    }
    
    try {
      // Check basic table info
      if (jobInfo.getTableInfo() == null) {
        return false;
      }
      
      // Check data columns
      if (jobInfo.getTableInfo().getDataColumns() == null) {
        return false;
      }
      
      // Additional validation can be added here as needed
      return true;
      
    } catch (Exception e) {
      LOG.debug("InputJobInfo validation failed: " + e.getMessage());
      return false;
    }
  }
}