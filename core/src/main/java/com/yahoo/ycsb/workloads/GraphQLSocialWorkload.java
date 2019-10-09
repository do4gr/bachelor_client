/**
 * Copyright (c) 2010 Yahoo! Inc., Copyright (c) 2016-2017 YCSB contributors. All rights reserved.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.workloads;

import com.yahoo.ycsb.*;
import com.yahoo.ycsb.generator.*;
import com.yahoo.ycsb.measurements.Measurements;
import java.util.HashMap;
import java.util.Properties;

/**
 This represents a Social Network application setting
 with the following datamodel in GraphQL SDL.
 This makes no assumptions about requests, except that
 they are named in the form [requestName]_[table to
 draw key parameter from]. The requests are grouped
 into READ, CREATE, UPDATE and are specified
 in the workload configuration  file.

 type User {
   id: ID!
   firstName: String!
   lastName: String!
   age: Int
   email: String
   password: String
   posts: [Post]
   comments: [Comment]
   likes: [Like]
   friendWith: [User]
   friendOf: [User]
   groups: [Group]
 }

 type Post {
   id: ID!
   content: String
   author: User
   comments: [Comment]
   likes: [Like]
 }

 type Comment {
   id: ID!
   content: String
   author: User
   post: Post
   likes: [Like]
 }

 type Like {
   id: ID!
   user: User
   post: Post
   comment: Comment
 }

 type Group {
   id: ID!
   topic: String!
   description: String
   members: [User]
 }

 */

public class GraphQLSocialWorkload extends Workload {

  /**
   * The name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY = "table";

  /**
   * The default name of the database table to run queries against.
   */
  public static final String TABLENAME_PROPERTY_DEFAULT = "user";

  protected String table;

  /**
   * The name of the property for the proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY = "readproportion";

  /**
   * The default proportion of transactions that are reads.
   */
  public static final String READ_PROPORTION_PROPERTY_DEFAULT = "0.95";

  /**
   * The name of the property for the proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY = "updateproportion";

  /**
   * The default proportion of transactions that are updates.
   */
  public static final String UPDATE_PROPORTION_PROPERTY_DEFAULT = "0.05";

  /**
   * The name of the property for the proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY = "insertproportion";

  /**
   * The default proportion of transactions that are inserts.
   */
  public static final String INSERT_PROPORTION_PROPERTY_DEFAULT = "0.0";

  /**
   * How many times to retry when insertion of a single item to a DB fails.
   */
  public static final String INSERTION_RETRY_LIMIT = "core_workload_insertion_retry_limit";
  public static final String INSERTION_RETRY_LIMIT_DEFAULT = "1";

  /**
   * On average, how long to wait between the retries, in seconds.
   */
  public static final String INSERTION_RETRY_INTERVAL = "core_workload_insertion_retry_interval";
  public static final String INSERTION_RETRY_INTERVAL_DEFAULT = "3";
  protected DiscreteGenerator operationchooser;
  protected NumberGenerator scanlength;
  protected long fieldcount;
  protected int insertionRetryLimit;
  protected int insertionRetryInterval;
  protected MultiTableSupport multiTable;


  private Measurements measurements = Measurements.getMeasurements();

  static String[] getNames(Properties p, String type) {
    String raw = p.getProperty(type, "[]");
    return raw.substring(1, raw.length() - 1).split(",");
  }


  /**
   * Initialize the scenario.
   * Called once, in the main client thread, before any operations are started.
   */
  @Override
  public void init(Properties p) throws WorkloadException {
    multiTable = new MultiTableSupport(p);
    table = p.getProperty(TABLENAME_PROPERTY, TABLENAME_PROPERTY_DEFAULT);
    operationchooser = createOperationGenerator(p);

    insertionRetryLimit = Integer.parseInt(p.getProperty(
        INSERTION_RETRY_LIMIT, INSERTION_RETRY_LIMIT_DEFAULT));
    insertionRetryInterval = Integer.parseInt(p.getProperty(
        INSERTION_RETRY_INTERVAL, INSERTION_RETRY_INTERVAL_DEFAULT));
  }

  /**
   * Do one insert operation. Because it will be called concurrently from multiple client threads,
   * this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doInsert(DB db, Object threadstate) {
    int keynum = multiTable.counters.get(table).keysequence.nextValue().intValue();

    String dbkey = multiTable.buildKeyName(keynum, table);
    HashMap<String, ByteIterator> values = new HashMap<>();

    Status status;
    int numOfRetries = 0;
    do {
      status = db.insert(table, dbkey, values);
      if (null != status && status.isOk()) {
        break;
      }
      // Retry if configured. Without retrying, the load process will fail
      // even if one single insertion fails. User can optionally configure
      // an insertion retry limit (default is 0) to enable retry.
      if (++numOfRetries <= insertionRetryLimit) {
        System.err.println("Retrying insertion, retry count: " + numOfRetries);
        try {
          // Sleep for a random number between [0.8, 1.2)*insertionRetryInterval.
          int sleepTime = (int) (1000 * insertionRetryInterval * (0.8 + 0.4 * Math.random()));
          Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
          break;
        }

      } else {
        System.err.println("Error inserting, not retrying any more. number of attempts: " + numOfRetries +
            "Insertion Retry Limit: " + insertionRetryLimit);
        break;

      }
    } while (true);

    return null != status && status.isOk();
  }

  /**
   * Do one transaction operation. Because it will be called concurrently from multiple client
   * threads, this function must be thread safe. However, avoid synchronized, or the threads will block waiting
   * for each other, and it will be difficult to reach the target throughput. Ideally, this function would
   * have no side effects other than DB operations.
   */
  @Override
  public boolean doTransaction(DB db, Object threadstate) {
    String operation = operationchooser.nextString();
    if (operation == null) {
      return false;
    }

    if (operation.startsWith("read.")) {
      doTransactionRead(db, operation.substring(5));
    } else if (operation.startsWith("update.")) {
      doTransactionUpdate(db, operation.substring(7));
    } else if (operation.startsWith("insert.")) {
      doTransactionInsert(db, operation.substring(7));
    } else {
      doTransactionRead(db, operation);
    }

    return true;
  }


  public void doTransactionRead(DB db, String operation) {
    String query = operation.split("_")[0];
    String tablename = operation.split("_")[1];
    db.read(query, multiTable.nextKeyname(tablename), null, null);
  }

  public void doTransactionUpdate(DB db, String operation) {
    String query = operation.split("_")[0];
    String tablename = operation.split("_")[1];
    db.update(query, multiTable.nextKeyname(tablename), null);
  }

  public void doTransactionInsert(DB db, String operation) {
    String query = operation.split("_")[0];
    db.insert(query, "", null);
  }

  static void addQueryProportions(Properties p, DiscreteGenerator operationchooser, String op, Double opProportion) {
    for (String queryWithTable : getNames(p,  op +".queries")) {
      String query = queryWithTable.split("_")[0];
      final double proportion = Double.parseDouble(
          p.getProperty(op + "." + query + ".proportion", "0"));
      if (proportion > 0) {
        operationchooser.addValue(opProportion * proportion, op + "." + queryWithTable);
      }
    }
  }

  /**
   * Creates a weighted discrete values with database operations for a workload to perform.
   * Weights/proportions are read from the properties list and defaults are used
   * when values are not configured.
   * Current operations are "READ", "UPDATE", "INSERT".
   *
   * @param p The properties list to pull weights from.
   * @return A generator that can be used to determine the next operation to perform.
   * @throws IllegalArgumentException if the properties object was null.
   */
  protected static DiscreteGenerator createOperationGenerator(final Properties p) {
    if (p == null) {
      throw new IllegalArgumentException("Properties object cannot be null");
    }
    final double readproportion = Double.parseDouble(
        p.getProperty(READ_PROPORTION_PROPERTY, READ_PROPORTION_PROPERTY_DEFAULT));
    final double updateproportion = Double.parseDouble(
        p.getProperty(UPDATE_PROPORTION_PROPERTY, UPDATE_PROPORTION_PROPERTY_DEFAULT));
    final double insertproportion = Double.parseDouble(
        p.getProperty(INSERT_PROPORTION_PROPERTY, INSERT_PROPORTION_PROPERTY_DEFAULT));
    final DiscreteGenerator operationchooser = new DiscreteGenerator();

    addQueryProportions(p, operationchooser, "read", readproportion);
    addQueryProportions(p, operationchooser, "update", updateproportion);
    addQueryProportions(p, operationchooser, "insert", insertproportion);

    return operationchooser;
  }
}
