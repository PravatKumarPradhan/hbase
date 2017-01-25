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

package org.apache.hadoop.hbase.replication;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.hadoop.hbase.classification.InterfaceAudience;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.HBaseInterfaceAudience;
import org.apache.hadoop.hbase.wal.WAL.Entry;

/**
 * A {@link WALEntryFilter} which contains multiple filters and applies them
 * in chain order
 */
@InterfaceAudience.LimitedPrivate(HBaseInterfaceAudience.REPLICATION)
public class ChainWALEntryFilter implements WALEntryFilter {

  private final WALEntryFilter[] filters;
  private WALCellFilter[] cellFilters;

  public ChainWALEntryFilter(WALEntryFilter...filters) {
    this.filters = filters;
    initCellFilters();
  }

  public ChainWALEntryFilter(List<WALEntryFilter> filters) {
    ArrayList<WALEntryFilter> rawFilters = new ArrayList<>(filters.size());
    // flatten the chains
    for (WALEntryFilter filter : filters) {
      if (filter instanceof ChainWALEntryFilter) {
        Collections.addAll(rawFilters, ((ChainWALEntryFilter) filter).filters);
      } else {
        rawFilters.add(filter);
      }
    }
    this.filters = rawFilters.toArray(new WALEntryFilter[rawFilters.size()]);
    initCellFilters();
  }

  public void initCellFilters() {
    ArrayList<WALCellFilter> cellFilters = new ArrayList<>(filters.length);
    for (WALEntryFilter filter : filters) {
      if (filter instanceof WALCellFilter) {
        cellFilters.add((WALCellFilter) filter);
      }
    }
    this.cellFilters = cellFilters.toArray(new WALCellFilter[cellFilters.size()]);
  }

  @Override
  public Entry filter(Entry entry) {
    for (WALEntryFilter filter : filters) {
      if (entry == null) {
        return null;
      }
      entry = filter.filter(entry);
    }
    filterCells(entry);
    return entry;
  }

  private void filterCells(Entry entry) {
    if (entry == null || cellFilters.length == 0) {
      return;
    }
    ArrayList<Cell> cells = entry.getEdit().getCells();
    int size = cells.size();
    for (int i = size - 1; i >= 0; i--) {
      Cell cell = cells.get(i);
      for (WALCellFilter filter : cellFilters) {
        cell = filter.filterCell(entry, cell);
        if (cell != null) {
          cells.set(i, cell);
        } else {
          cells.remove(i);
          break;
        }
      }
    }
    if (cells.size() < size / 2) {
      cells.trimToSize();
    }
  }
}
