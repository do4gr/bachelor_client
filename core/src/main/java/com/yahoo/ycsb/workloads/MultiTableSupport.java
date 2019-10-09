package com.yahoo.ycsb.workloads;

import com.yahoo.ycsb.Utils;
import com.yahoo.ycsb.generator.ExponentialGenerator;

import java.util.HashMap;
import java.util.Properties;

/**
 * Bundles needed functionality to express multitable support.
 */
public class MultiTableSupport {
  public static final String ZERO_PADDING_PROPERTY = "zeropadding";
  public static final String ZERO_PADDING_PROPERTY_DEFAULT = "1";
  public static final String INSERT_ORDER_PROPERTY = "insertorder";
  public static final String INSERT_ORDER_PROPERTY_DEFAULT = "hashed";
  protected boolean orderedinserts;
  protected int zeropadding;

  protected HashMap<String, CountClass> counters = new HashMap<>();


  public MultiTableSupport(Properties p){

    for (String model : getNames(p, "models")) {
      counters.put(model, new CountClass(p, model));
    }

    for (String relation : getNames(p, "relations")) {
      counters.put(relation, new CountClass(p, relation));
    }

    zeropadding =
        Integer.parseInt(p.getProperty(ZERO_PADDING_PROPERTY, ZERO_PADDING_PROPERTY_DEFAULT));

    if (p.getProperty(INSERT_ORDER_PROPERTY, INSERT_ORDER_PROPERTY_DEFAULT).compareTo("hashed") == 0) {
      orderedinserts = false;
    } else {
      orderedinserts = true;
    }

  }

  static String[] getNames(Properties p, String type) {
    String raw = p.getProperty(type, "[]");
    return raw.substring(1, raw.length() - 1).split(",");
  }

  public String buildKeyName(long keynum, String table2) {
    if (!orderedinserts) {
      keynum = Utils.hash(keynum);
    }
    String value = Long.toString(keynum);
    int fill = zeropadding - value.length();
    String prekey = table2;
    for (int i = 0; i < fill; i++) {
      prekey += '0';
    }
    return prekey + value;
  }

  public String buildTransactionKeyName(String table2){
    int keynum = counters.get(table2).keysequence.nextValue().intValue();

    return buildKeyName(keynum, table2 + "X");
  }


  public String nextKeyname(String name) {
    long keynum;
    CountClass counter = counters.get(name);
    if (counter.keychooser instanceof ExponentialGenerator) {
      do {
        keynum = counter.transactioninsertkeysequence.lastValue() -
            counter.keychooser.nextValue().intValue();
      } while (keynum < 0);
    } else {
      do {
        keynum = counter.keychooser.nextValue().intValue();
      } while (keynum > counter.transactioninsertkeysequence.lastValue());
    }
    return buildKeyName(keynum, name);
  }

}
