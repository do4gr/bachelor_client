/**
 * Copyright (c) 2016 YCSB contributors. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you
 * may not use this file except in compliance with the License. You
 * may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License. See accompanying
 * LICENSE file.
 */

package com.yahoo.ycsb.webservice.neo4j.social;

import com.yahoo.ycsb.ByteIterator;
import com.yahoo.ycsb.DB;
import com.yahoo.ycsb.DBException;
import com.yahoo.ycsb.Status;
import com.yahoo.ycsb.workloads.MultiTableSupport;
import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpEntityEnclosingRequestBase;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;

import java.io.*;
import java.util.*;

import static org.apache.commons.lang3.RandomStringUtils.random;

/**
 * Class responsible for making web service requests for benchmarking purpose.
 * Using Apache HttpClient over standard Java HTTP API as this is more flexible
 * and provides better functionality. For example HttpClient can automatically
 * handle redirects and proxy authentication which the standard Java API can't.
 */
public class Neo4jClient extends DB {

  private static final String HTTP_ENDPOINT = "http.endpoint";
  private static final String CON_TIMEOUT = "timeout.con";
  private static final String READ_TIMEOUT = "timeout.read";
  private static final String EXEC_TIMEOUT = "timeout.exec";
  private static final String LOG_ENABLED = "log.enable";
  private static final String HEADERS = "headers";
  private boolean logEnabled;
  private String httpEndpoint;
  private Properties props;
  private String[] headers;
  private CloseableHttpClient client;
  private int conTimeout = 10000;
  private int readTimeout = 10000;
  private int execTimeout = 10000;
  private volatile Criteria requestTimedout = new Criteria(false);
  protected MultiTableSupport multiTable;

  @Override
  public void init() throws DBException {
    props = getProperties();
    httpEndpoint = props.getProperty(HTTP_ENDPOINT, "http://localhost:7474/graphql/");
    conTimeout = Integer.valueOf(props.getProperty(CON_TIMEOUT, "10")) * 1000;
    readTimeout = Integer.valueOf(props.getProperty(READ_TIMEOUT, "10")) * 1000;
    execTimeout = Integer.valueOf(props.getProperty(EXEC_TIMEOUT, "10")) * 1000;
    logEnabled = Boolean.valueOf(props.getProperty(LOG_ENABLED, "false").trim());
    headers = props.getProperty(HEADERS, "Content-Type application/json").trim().split(" ");
    multiTable = new MultiTableSupport(props);

    setupClient();
  }

  private void setupClient() {
    RequestConfig.Builder requestBuilder = RequestConfig.custom();
    requestBuilder = requestBuilder.setConnectTimeout(conTimeout);
    requestBuilder = requestBuilder.setConnectionRequestTimeout(readTimeout);
    requestBuilder = requestBuilder.setSocketTimeout(readTimeout);
    HttpClientBuilder clientBuilder = HttpClientBuilder.create().setDefaultRequestConfig(requestBuilder.build());
    this.client = clientBuilder.setConnectionManagerShared(true).build();
  }

  @Override
  public Status read(String operation, String key, Set<String> fields, Map<String, ByteIterator> result) {
    Status status;
    try {

      String queryName = "";
      String filter = "";
      String returnValue = "";

      if(operation.equals("userByIdWithPostsAndCommentsAndAuthors")){
        queryName = "User";
        filter = "(id: \\\"" + key +  "\\\")";
        returnValue= "{firstName, lastName, posts { content author {firstName, lastName}" +
            " comments{ content author {firstName lastName }}}}";
      } else if (operation.equals("groupByIdWithMemberIds")){
        queryName = "Group";
        filter = "(id: \\\"" + key +  "\\\")";
        returnValue= "{ topic description  members {firstName lastName}}";
      } else if (operation.equals("postByIdWithFirstTenComments")){
        queryName = "Post";
        filter = "(id: \\\"" + key +  "\\\")";
        returnValue= "{ content, comments (first: 10) {id, content}}";
      }

      String request = new StringBuilder("query{")
          .append(queryName)
          .append(filter)
          .append(returnValue)
          .append("}")
          .toString();

      status = httpExecute(new HttpPost(httpEndpoint), request);
    } catch (Exception e) {
      status = handleExceptions(e, operation);
    }
    return status;
  }

  private String randString(int length) {
    //  Uppercase Alphabet range only instead of full ASCII to prevent escaping problems
    String string = random(length, 65, 90, false, false);
    return  " \\\"" + string + "\\\" ";
  }

  @Override
  public Status insert(String operation, String key, Map<String, ByteIterator> values) {
    Status status;
    try {
      String mutation1 = " ";
      String mutation2 = " ";
      String mutation3 = " ";

// TRANSACTIONS
      if(operation.equals("createSingleUser")){
        String nonIdFields = " firstName:" + randString(6) + ",lastName:" + randString(10) + ", " +
            "email:" + randString(25) + ", password: " + randString(10);
        mutation1 = "first: createUser(id:\\\"" + multiTable.buildTransactionKeyName("user") +"\\\", "
            + nonIdFields +")";
      } else if (operation.equals("createAndConnectSingleLike")){
        mutation1 = "first: createLike(id:\\\"" + multiTable.buildTransactionKeyName("like") +"\\\")";
        mutation2 = "second: addPostLikes(id:\\\"" + multiTable.nextKeyname("post") +"\\\"," +
            " likes:[\\\""+ key +"\\\"])";
        mutation3 = "third: addUserLikes(id:\\\"" + multiTable.nextKeyname("user") +"\\\"," +
            " likes:[\\\""+ key +"\\\"])";
      } else if (operation.equals("createAndConnectSingleComment")){
        mutation1 = "first: createComment(id:\\\"" + multiTable.buildTransactionKeyName("comment")
            +"\\\" content:" + randString(50) + ")";
        mutation2 = "second: addPostComments(id:\\\"" + multiTable.nextKeyname("post") +"\\\"," +
            " comments:[\\\""+ key +"\\\"])";
        mutation3 = "third: addUserComments(id:\\\"" + multiTable.nextKeyname("user") +"\\\"," +
            " comments:[\\\""+ key +"\\\"])";
// LOAD
      } else if (operation.equals("user")) {
        String nonIdFields = " firstName:" + randString(6) + ",lastName:" + randString(10) + ", " +
            "email:" + randString(25) + ", password:" + randString(10);

        mutation1 = "first: createUser(id:\\\"" + key +"\\\", " + nonIdFields +")";
      } else if (operation.equals("post")){
        mutation1 = "first: createPost(id:\\\"" + key +"\\\" content: " + randString(200) + ")";
        mutation2 = "second: addUserPosts(id:\\\"" + multiTable.nextKeyname("user") +"\\\"," +
            " posts:[\\\""+ key +"\\\"])";
      } else if (operation.equals("comment")){
        mutation1 = "first: createComment(id:\\\"" + key +"\\\" content:" + randString(50) + ")";
        mutation2 = "second: addPostComments(id:\\\"" + multiTable.nextKeyname("post") +"\\\"," +
            " comments:[\\\""+ key +"\\\"])";
        mutation3 = "third: addUserComments(id:\\\"" + multiTable.nextKeyname("user") +"\\\"," +
            " comments:[\\\""+ key +"\\\"])";
      } else if (operation.equals("like")){
        mutation1 = "first: createLike(id:\\\"" + key +"\\\")";
        mutation2 = "second: addPostLikes(id:\\\"" + multiTable.nextKeyname("post") +"\\\"," +
            " likes:[\\\""+ key +"\\\"])";
        mutation3 = "third: addUserLikes(id:\\\"" + multiTable.nextKeyname("user") +"\\\"," +
            " likes:[\\\""+ key +"\\\"])";
      } else if (operation.equals("group")){

        String nonIdFields = " topic:" + randString(10) + ", description:" + randString(100);
        mutation1 = "first: createGroup(id:\\\"" + key +"\\\", " + nonIdFields +")";
      } else if (operation.equals("friendship")){
        mutation1 = "first: addUserFriendWith(id:\\\"" + multiTable.nextKeyname("user") +"\\\", " +
            "friendWith:[\\\"" +  "user" + multiTable.nextKeyname("user") + "\\\"])";
      }

      String request = new StringBuilder("mutation{")
          .append(mutation1)
          .append(mutation2)
          .append(mutation3)
          .append("}")
          .toString();

      status = httpExecute(new HttpPost(httpEndpoint), request);
    } catch (Exception e) {
      status = handleExceptions(e, operation);
    }
    return status;
  }


  @Override
  public Status update(String operation, String key, Map<String, ByteIterator> values) {
    Status status;
    try {

      String mutation1 = " ";

      if(operation.equals("updateUserByIdSetFriendWith")){
        mutation1 = "first: addUserFriendWith(id:\\\"" + key +"\\\", " +
          "friendWith:[\\\"" +  multiTable.nextKeyname("user") + "\\\"])";
      } else if (operation.equals("updatePostByIdSetContent")){
        mutation1 = "first: mergePost(id:\\\"" + key +"\\\", content:" + randString(300) + ")";
      } else if (operation.equals("updateCommentByIdSetContent")){
        mutation1 = "first: mergeComment(id:\\\"" + key +"\\\", content:" + randString(150) + ")";
      }

      String request = new StringBuilder("mutation{")
          .append(mutation1)
          .append("}")
          .toString();

      status = httpExecute(new HttpPost(httpEndpoint), request);
    } catch (Exception e) {
      status = handleExceptions(e, operation);
    }
    return status;
  }

  @Override
  public Status delete(String operation, String endpoint) {
    return Status.NOT_IMPLEMENTED;
  }

  @Override
  public Status scan(String operation, String startkey, int recordcount, Set<String> fields,
                     Vector<HashMap<String, ByteIterator>> result) {
    return Status.NOT_IMPLEMENTED;
  }

  private Status handleExceptions(Exception e, String operation) {
    if (logEnabled) {
      System.err.println(new StringBuilder(operation).append(" | ")
          .append(e.getClass().getName()).append(" occured | Error message: ")
          .append(e.getMessage()).toString());
    }

    if (e instanceof ClientProtocolException) {
      return Status.BAD_REQUEST;
    }
    return Status.ERROR;
  }

  private Status httpExecute(HttpEntityEnclosingRequestBase request, String data) throws IOException {

    String wrappedData = new StringBuilder("{\"query\": \"").append(data).append("\"}").toString();


    requestTimedout.setIsSatisfied(false);
    Thread timer = new Thread(new Timer(execTimeout, requestTimedout));
    timer.start();
    int responseCode = 200;
    StringBuffer responseContent = new StringBuffer();
    for (int i = 0; i < headers.length; i = i + 2) {
      request.setHeader(headers[i], headers[i + 1]);
    }
    InputStreamEntity reqEntity = new InputStreamEntity(new ByteArrayInputStream(wrappedData.getBytes()),
        ContentType.APPLICATION_FORM_URLENCODED);
    reqEntity.setChunked(true);
    request.setEntity(reqEntity);
    CloseableHttpResponse response = client.execute(request);
    responseCode = response.getStatusLine().getStatusCode();
    HttpEntity responseEntity = response.getEntity();
    // If null entity don't bother about connection release.
    if (responseEntity != null) {
      InputStream stream = responseEntity.getContent();
      BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"));
      String line = "";
      while ((line = reader.readLine()) != null) {
        if (requestTimedout.isSatisfied()) {
          // Must avoid memory leak.
          reader.close();
          stream.close();
          EntityUtils.consumeQuietly(responseEntity);
          response.close();
          client.close();
          throw new TimeoutException();
        }
        responseContent.append(line);
      }
      timer.interrupt();

      if(logEnabled){
        System.err.print("REQUEST: " + data + "\n");
        System.err.print("RESPONSE: " + responseContent + "\n");
      }
      // Closing the input stream will trigger connection release.
      stream.close();
    }
    EntityUtils.consumeQuietly(responseEntity);
    response.close();
    client.close();
    return getStatus(responseCode, responseContent.toString());
  }

  // Maps HTTP status codes to YCSB status codes.
  private Status getStatus(int responseCode, String responseContent) {
    int rc = responseCode / 100;
    if (responseCode == 400) {
      return Status.BAD_REQUEST;
    } else if (responseCode == 403) {
      return Status.FORBIDDEN;
    } else if (responseCode == 404) {
      return Status.NOT_FOUND;
    } else if (responseCode == 501) {
      return Status.NOT_IMPLEMENTED;
    } else if (responseCode == 503) {
      return Status.SERVICE_UNAVAILABLE;
    } else if (rc == 5) {
      return Status.ERROR;
    } else if (rc ==2 && responseContent.contains("\"errors\":[{")){
      System.err.print("FAILED REQUEST RESPONSE: " + responseContent + "\n");
      return Status.ERROR;
    }
    return Status.OK;
  }

  /**
   * Marks the input {@link Criteria} as satisfied when the input time has elapsed.
   */
  class Timer implements Runnable {

    private long timeout;
    private Criteria timedout;

    public Timer(long timeout, Criteria timedout) {
      this.timedout = timedout;
      this.timeout = timeout;
    }

    @Override
    public void run() {
      try {
        Thread.sleep(timeout);
        this.timedout.setIsSatisfied(true);
      } catch (InterruptedException e) {
        // Do nothing.
      }
    }

  }

  /**
   * Sets the flag when a criteria is fulfilled.
   */
  class Criteria {

    private boolean isSatisfied;

    public Criteria(boolean isSatisfied) {
      this.isSatisfied = isSatisfied;
    }

    public boolean isSatisfied() {
      return isSatisfied;
    }

    public void setIsSatisfied(boolean satisfied) {
      this.isSatisfied = satisfied;
    }

  }

  /**
   * Private exception class for execution timeout.
   */
  class TimeoutException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public TimeoutException() {
      super("HTTP Request exceeded execution time limit.");
    }

  }

}
