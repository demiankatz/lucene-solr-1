/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.lucene.search;


import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.lucene.analysis.MockAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.codecs.FilterCodec;
import org.apache.lucene.codecs.PointFormat;
import org.apache.lucene.codecs.PointReader;
import org.apache.lucene.codecs.PointWriter;
import org.apache.lucene.codecs.lucene60.Lucene60PointReader;
import org.apache.lucene.codecs.lucene60.Lucene60PointWriter;
import org.apache.lucene.document.BinaryPoint;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.DoublePoint;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FloatPoint;
import org.apache.lucene.document.IntPoint;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.NumericDocValuesField;
import org.apache.lucene.document.SortedNumericDocValuesField;
import org.apache.lucene.document.StringField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LeafReaderContext;
import org.apache.lucene.index.MultiDocValues;
import org.apache.lucene.index.NumericDocValues;
import org.apache.lucene.index.PointValues;
import org.apache.lucene.index.RandomIndexWriter;
import org.apache.lucene.index.SegmentReadState;
import org.apache.lucene.index.SegmentWriteState;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.util.BytesRef;
import org.apache.lucene.util.BytesRefIterator;
import org.apache.lucene.util.IOUtils;
import org.apache.lucene.util.LuceneTestCase;
import org.apache.lucene.util.NumericUtils;
import org.apache.lucene.util.StringHelper;
import org.apache.lucene.util.TestUtil;
import org.junit.BeforeClass;

public class TestPointQueries extends LuceneTestCase {

  // Controls what range of values we randomly generate, so we sometimes test narrow ranges:
  static long valueMid;
  static int valueRange;

  @BeforeClass
  public static void beforeClass() {
    if (random().nextBoolean()) {
      valueMid = random().nextLong();
      if (random().nextBoolean()) {
        // Wide range
        valueRange = TestUtil.nextInt(random(), 1, Integer.MAX_VALUE);
      } else {
        // Narrow range
        valueRange = TestUtil.nextInt(random(), 1, 100000);
      }
      if (VERBOSE) {
        System.out.println("TEST: will generate long values " + valueMid + " +/- " + valueRange);
      }
    } else {
      // All longs
      valueRange = 0;
      if (VERBOSE) {
        System.out.println("TEST: will generate all long values");
      }
    }
  }

  public void testBasicInts() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));

    Document doc = new Document();
    doc.add(new IntPoint("point", -7));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new IntPoint("point", 0));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new IntPoint("point", 3));
    w.addDocument(doc);

    DirectoryReader r = DirectoryReader.open(w);
    IndexSearcher s = new IndexSearcher(r);
    assertEquals(2, s.count(IntPoint.newRangeQuery("point", -8, false, 1, false)));
    assertEquals(3, s.count(IntPoint.newRangeQuery("point", -7, true, 3, true)));
    assertEquals(1, s.count(IntPoint.newExactQuery("point", -7)));
    assertEquals(0, s.count(IntPoint.newExactQuery("point", -6)));
    w.close();
    r.close();
    dir.close();
  }

  public void testBasicFloats() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));

    Document doc = new Document();
    doc.add(new FloatPoint("point", -7.0f));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new FloatPoint("point", 0.0f));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new FloatPoint("point", 3.0f));
    w.addDocument(doc);

    DirectoryReader r = DirectoryReader.open(w);
    IndexSearcher s = new IndexSearcher(r);
    assertEquals(2, s.count(FloatPoint.newRangeQuery("point", -8.0f, false, 1.0f, false)));
    assertEquals(3, s.count(FloatPoint.newRangeQuery("point", -7.0f, true, 3.0f, true)));
    assertEquals(1, s.count(FloatPoint.newExactQuery("point", -7.0f)));
    assertEquals(0, s.count(FloatPoint.newExactQuery("point", -6.0f)));
    w.close();
    r.close();
    dir.close();
  }

  public void testBasicLongs() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));

    Document doc = new Document();
    doc.add(new LongPoint("point", -7));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new LongPoint("point", 0));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new LongPoint("point", 3));
    w.addDocument(doc);

    DirectoryReader r = DirectoryReader.open(w);
    IndexSearcher s = new IndexSearcher(r);
    assertEquals(2, s.count(LongPoint.newRangeQuery("point", -8L, false, 1L, false)));
    assertEquals(3, s.count(LongPoint.newRangeQuery("point", -7L, true, 3L, true)));
    assertEquals(1, s.count(LongPoint.newExactQuery("point", -7L)));
    assertEquals(0, s.count(LongPoint.newExactQuery("point", -6L)));
    w.close();
    r.close();
    dir.close();
  }

  public void testBasicDoubles() throws Exception {
    Directory dir = newDirectory();
    IndexWriter w = new IndexWriter(dir, new IndexWriterConfig(new MockAnalyzer(random())));

    Document doc = new Document();
    doc.add(new DoublePoint("point", -7.0));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new DoublePoint("point", 0.0));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new DoublePoint("point", 3.0));
    w.addDocument(doc);

    DirectoryReader r = DirectoryReader.open(w);
    IndexSearcher s = new IndexSearcher(r);
    assertEquals(2, s.count(DoublePoint.newRangeQuery("point", -8.0, false, 1.0, false)));
    assertEquals(3, s.count(DoublePoint.newRangeQuery("point", -7.0, true, 3.0, true)));
    assertEquals(1, s.count(DoublePoint.newExactQuery("point", -7.0)));
    assertEquals(0, s.count(DoublePoint.newExactQuery("point", -6.0)));
    w.close();
    r.close();
    dir.close();
  }

  public void testAllEqual() throws Exception {
    int numValues = atLeast(10000);
    long value = randomValue(false);
    long[] values = new long[numValues];

    if (VERBOSE) {
      System.out.println("TEST: use same value=" + value);
    }
    Arrays.fill(values, value);

    verifyLongs(values, null);
  }

  public void testRandomLongsTiny() throws Exception {
    // Make sure single-leaf-node case is OK:
    doTestRandomLongs(10);
  }

  public void testRandomLongsMedium() throws Exception {
    doTestRandomLongs(10000);
  }

  @Nightly
  public void testRandomLongsBig() throws Exception {
    doTestRandomLongs(200000);
  }

  private void doTestRandomLongs(int count) throws Exception {

    int numValues = atLeast(count);

    if (VERBOSE) {
      System.out.println("TEST: numValues=" + numValues);
    }

    long[] values = new long[numValues];
    int[] ids = new int[numValues];

    boolean singleValued = random().nextBoolean();

    int sameValuePct = random().nextInt(100);

    int id = 0;
    for (int ord=0;ord<numValues;ord++) {
      if (ord > 0 && random().nextInt(100) < sameValuePct) {
        // Identical to old value
        values[ord] = values[random().nextInt(ord)];
      } else {
        values[ord] = randomValue(false);
      }

      ids[ord] = id;
      if (singleValued || random().nextInt(2) == 1) {
        id++;
      }
    }

    verifyLongs(values, ids);
  }

  public void testLongEncode() {
    for(int i=0;i<10000;i++) {
      long v = random().nextLong();
      byte[] tmp = new byte[8];
      NumericUtils.longToBytes(v, tmp, 0);
      long v2 = NumericUtils.bytesToLong(tmp, 0);
      assertEquals("got bytes=" + Arrays.toString(tmp), v, v2);
    }
  }

  // verify for long values
  private static void verifyLongs(long[] values, int[] ids) throws Exception {
    IndexWriterConfig iwc = newIndexWriterConfig();

    // Else we can get O(N^2) merging:
    int mbd = iwc.getMaxBufferedDocs();
    if (mbd != -1 && mbd < values.length/100) {
      iwc.setMaxBufferedDocs(values.length/100);
    }
    iwc.setCodec(getCodec());
    Directory dir;
    if (values.length > 100000) {
      dir = newMaybeVirusCheckingFSDirectory(createTempDir("TestRangeTree"));
    } else {
      dir = newMaybeVirusCheckingDirectory();
    }

    int missingPct = random().nextInt(100);
    int deletedPct = random().nextInt(100);
    if (VERBOSE) {
      System.out.println("  missingPct=" + missingPct);
      System.out.println("  deletedPct=" + deletedPct);
    }

    BitSet missing = new BitSet();
    BitSet deleted = new BitSet();

    Document doc = null;
    int lastID = -1;

    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    for(int ord=0;ord<values.length;ord++) {

      int id;
      if (ids == null) {
        id = ord;
      } else {
        id = ids[ord];
      }
      if (id != lastID) {
        if (random().nextInt(100) < missingPct) {
          missing.set(id);
          if (VERBOSE) {
            System.out.println("  missing id=" + id);
          }
        }

        if (doc != null) {
          w.addDocument(doc);
          if (random().nextInt(100) < deletedPct) {
            int idToDelete = random().nextInt(id);
            w.deleteDocuments(new Term("id", ""+idToDelete));
            deleted.set(idToDelete);
            if (VERBOSE) {
              System.out.println("  delete id=" + idToDelete);
            }
          }
        }

        doc = new Document();
        doc.add(newStringField("id", ""+id, Field.Store.NO));
        doc.add(new NumericDocValuesField("id", id));
        lastID = id;
      }

      if (missing.get(id) == false) {
        doc.add(new LongPoint("sn_value", values[id]));
        byte[] bytes = new byte[8];
        NumericUtils.longToBytes(values[id], bytes, 0);
        doc.add(new BinaryPoint("ss_value", bytes));
      }
    }

    w.addDocument(doc);

    if (random().nextBoolean()) {
      if (VERBOSE) {
        System.out.println("  forceMerge(1)");
      }
      w.forceMerge(1);
    }
    final IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);

    int numThreads = TestUtil.nextInt(random(), 2, 5);

    if (VERBOSE) {
      System.out.println("TEST: use " + numThreads + " query threads; searcher=" + s);
    }

    List<Thread> threads = new ArrayList<>();
    final int iters = atLeast(100);

    final CountDownLatch startingGun = new CountDownLatch(1);
    final AtomicBoolean failed = new AtomicBoolean();

    for(int i=0;i<numThreads;i++) {
      Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              _run();
            } catch (Exception e) {
              failed.set(true);
              throw new RuntimeException(e);
            }
          }

          private void _run() throws Exception {
            startingGun.await();

            NumericDocValues docIDToID = MultiDocValues.getNumericValues(r, "id");
            for (int iter=0;iter<iters && failed.get() == false;iter++) {
              Long lower = randomValue(true);
              Long upper = randomValue(true);

              if (lower != null && upper != null && upper < lower) {
                long x = lower;
                lower = upper;
                upper = x;
              }

              boolean includeLower = random().nextBoolean();
              boolean includeUpper = random().nextBoolean();
              Query query;

              if (VERBOSE) {
                System.out.println("\n" + Thread.currentThread().getName() + ": TEST: iter=" + iter + " value=" + lower + " (inclusive?=" + includeLower + ") TO " + upper + " (inclusive?=" + includeUpper + ")");
                byte[] tmp = new byte[8];
                if (lower != null) {
                  NumericUtils.longToBytes(lower, tmp, 0);
                  System.out.println("  lower bytes=" + Arrays.toString(tmp));
                }
                if (upper != null) {
                  NumericUtils.longToBytes(upper, tmp, 0);
                  System.out.println("  upper bytes=" + Arrays.toString(tmp));
                }
              }

              if (random().nextBoolean()) {
                query = LongPoint.newRangeQuery("sn_value", lower, includeLower, upper, includeUpper);
              } else {
                byte[] lowerBytes;
                if (lower == null) {
                  lowerBytes = null;
                } else {
                  lowerBytes = new byte[8];
                  NumericUtils.longToBytes(lower, lowerBytes, 0);
                }
                byte[] upperBytes;
                if (upper == null) {
                  upperBytes = null;
                } else {
                  upperBytes = new byte[8];
                  NumericUtils.longToBytes(upper, upperBytes, 0);
                }
                query = BinaryPoint.newRangeQuery("ss_value", lowerBytes, includeLower, upperBytes, includeUpper);
              }

              if (VERBOSE) {
                System.out.println(Thread.currentThread().getName() + ":  using query: " + query);
              }

              final BitSet hits = new BitSet();
              s.search(query, new SimpleCollector() {

                  private int docBase;

                  @Override
                  public boolean needsScores() {
                    return false;
                  }

                  @Override
                  protected void doSetNextReader(LeafReaderContext context) throws IOException {
                    docBase = context.docBase;
                  }

                  @Override
                  public void collect(int doc) {
                    hits.set(docBase+doc);
                  }
                });

              if (VERBOSE) {
                System.out.println(Thread.currentThread().getName() + ":  hitCount: " + hits.cardinality());
              }
      
              for(int docID=0;docID<r.maxDoc();docID++) {
                int id = (int) docIDToID.get(docID);
                boolean expected = missing.get(id) == false && deleted.get(id) == false && matches(lower, includeLower, upper, includeUpper, values[id]);
                if (hits.get(docID) != expected) {
                  // We do exact quantized comparison so the bbox query should never disagree:
                  fail(Thread.currentThread().getName() + ": iter=" + iter + " id=" + id + " docID=" + docID + " value=" + values[id] + " (range: " + lower + " TO " + upper + ") expected " + expected + " but got: " + hits.get(docID) + " deleted?=" + deleted.get(id) + " query=" + query);
                  }
                }
              }
            }
        };
      thread.setName("T" + i);
      thread.start();
      threads.add(thread);
    }
    startingGun.countDown();
    for(Thread thread : threads) {
      thread.join();
    }
    IOUtils.close(r, dir);
  }

  public void testRandomBinaryTiny() throws Exception {
    doTestRandomBinary(10);
  }

  public void testRandomBinaryMedium() throws Exception {
    doTestRandomBinary(10000);
  }

  @Nightly
  public void testRandomBinaryBig() throws Exception {
    doTestRandomBinary(200000);
  }

  private void doTestRandomBinary(int count) throws Exception {
    int numValues = TestUtil.nextInt(random(), count, count*2);
    int numBytesPerDim = TestUtil.nextInt(random(), 2, PointValues.MAX_NUM_BYTES);
    int numDims = TestUtil.nextInt(random(), 1, PointValues.MAX_DIMENSIONS);

    int sameValuePct = random().nextInt(100);

    byte[][][] docValues = new byte[numValues][][];

    boolean singleValued = random().nextBoolean();
    int[] ids = new int[numValues];

    int id = 0;
    for(int ord=0;ord<numValues;ord++) {
      if (ord > 0 && random().nextInt(100) < sameValuePct) {
        // Identical to old value
        docValues[ord] = docValues[random().nextInt(ord)];
      } else {
        // Make a new random value
        byte[][] values = new byte[numDims][];
        for(int dim=0;dim<numDims;dim++) {
          values[dim] = new byte[numBytesPerDim];
          random().nextBytes(values[dim]);
        }
        docValues[ord] = values;
      }
      ids[ord] = id;
      if (singleValued || random().nextInt(2) == 1) {
        id++;
      }
    }

    verifyBinary(docValues, ids, numBytesPerDim);
  }

  // verify for byte[][] values
  private void verifyBinary(byte[][][] docValues, int[] ids, int numBytesPerDim) throws Exception {
    IndexWriterConfig iwc = newIndexWriterConfig();

    int numDims = docValues[0].length;
    int bytesPerDim = docValues[0][0].length;

    // Else we can get O(N^2) merging:
    int mbd = iwc.getMaxBufferedDocs();
    if (mbd != -1 && mbd < docValues.length/100) {
      iwc.setMaxBufferedDocs(docValues.length/100);
    }
    iwc.setCodec(getCodec());

    Directory dir;
    if (docValues.length > 100000) {
      dir = newFSDirectory(createTempDir("TestPointQueries"));
    } else {
      dir = newDirectory();
    }

    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    int numValues = docValues.length;
    if (VERBOSE) {
      System.out.println("TEST: numValues=" + numValues + " numDims=" + numDims + " numBytesPerDim=" + numBytesPerDim);
    }

    int missingPct = random().nextInt(100);
    int deletedPct = random().nextInt(100);
    if (VERBOSE) {
      System.out.println("  missingPct=" + missingPct);
      System.out.println("  deletedPct=" + deletedPct);
    }

    BitSet missing = new BitSet();
    BitSet deleted = new BitSet();

    Document doc = null;
    int lastID = -1;

    for(int ord=0;ord<numValues;ord++) {
      int id = ids[ord];
      if (id != lastID) {
        if (random().nextInt(100) < missingPct) {
          missing.set(id);
          if (VERBOSE) {
            System.out.println("  missing id=" + id);
          }
        }

        if (doc != null) {
          w.addDocument(doc);
          if (random().nextInt(100) < deletedPct) {
            int idToDelete = random().nextInt(id);
            w.deleteDocuments(new Term("id", ""+idToDelete));
            deleted.set(idToDelete);
            if (VERBOSE) {
              System.out.println("  delete id=" + idToDelete);
            }
          }
        }

        doc = new Document();
        doc.add(newStringField("id", ""+id, Field.Store.NO));
        doc.add(new NumericDocValuesField("id", id));
        lastID = id;
      }

      if (missing.get(id) == false) {
        doc.add(new BinaryPoint("value", docValues[ord]));
        if (VERBOSE) {
          System.out.println("id=" + id);
          for(int dim=0;dim<numDims;dim++) {
            System.out.println("  dim=" + dim + " value=" + bytesToString(docValues[ord][dim]));
          }
        }
      }
    }

    w.addDocument(doc);

    if (random().nextBoolean()) {
      if (VERBOSE) {
        System.out.println("  forceMerge(1)");
      }
      w.forceMerge(1);
    }
    final IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);

    int numThreads = TestUtil.nextInt(random(), 2, 5);

    if (VERBOSE) {
      System.out.println("TEST: use " + numThreads + " query threads; searcher=" + s);
    }

    List<Thread> threads = new ArrayList<>();
    final int iters = atLeast(100);

    final CountDownLatch startingGun = new CountDownLatch(1);
    final AtomicBoolean failed = new AtomicBoolean();

    for(int i=0;i<numThreads;i++) {
      Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              _run();
            } catch (Exception e) {
              failed.set(true);
              throw new RuntimeException(e);
            }
          }

          private void _run() throws Exception {
            startingGun.await();

            NumericDocValues docIDToID = MultiDocValues.getNumericValues(r, "id");

            for (int iter=0;iter<iters && failed.get() == false;iter++) {

              boolean[] includeUpper = new boolean[numDims];
              boolean[] includeLower = new boolean[numDims];
              byte[][] lower = new byte[numDims][];
              byte[][] upper = new byte[numDims][];
              for(int dim=0;dim<numDims;dim++) {
                if (random().nextInt(5) != 1) {
                  lower[dim] = new byte[bytesPerDim];
                  random().nextBytes(lower[dim]);
                } else {
                  // open-ended on the lower bound
                }
                if (random().nextInt(5) != 1) {
                  upper[dim] = new byte[bytesPerDim];
                  random().nextBytes(upper[dim]);
                } else {
                  // open-ended on the upper bound
                }

                if (lower[dim] != null && upper[dim] != null && NumericUtils.compare(bytesPerDim, lower[dim], 0, upper[dim], 0) > 0) {
                  byte[] x = lower[dim];
                  lower[dim] = upper[dim];
                  upper[dim] = x;
                }

                includeLower[dim] = random().nextBoolean();
                includeUpper[dim] = random().nextBoolean();
              }

              if (VERBOSE) {
                System.out.println("\n" + Thread.currentThread().getName() + ": TEST: iter=" + iter);
                for(int dim=0;dim<numDims;dim++) {
                  System.out.println("  dim=" + dim + " " +
                                     bytesToString(lower[dim]) +
                                     " (inclusive?=" + includeLower[dim] + ") TO " +
                                     bytesToString(upper[dim]) +
                                     " (inclusive?=" + includeUpper[dim] + ")");
                }
              }

              Query query = BinaryPoint.newMultiRangeQuery("value", lower, includeLower, upper, includeUpper);

              if (VERBOSE) {
                System.out.println(Thread.currentThread().getName() + ":  using query: " + query);
              }

              final BitSet hits = new BitSet();
              s.search(query, new SimpleCollector() {

                  private int docBase;

                  @Override
                  public boolean needsScores() {
                    return false;
                  }

                  @Override
                  protected void doSetNextReader(LeafReaderContext context) throws IOException {
                    docBase = context.docBase;
                  }

                  @Override
                  public void collect(int doc) {
                    hits.set(docBase+doc);
                  }
                });

              if (VERBOSE) {
                System.out.println(Thread.currentThread().getName() + ":  hitCount: " + hits.cardinality());
              }

              BitSet expected = new BitSet();
              for(int ord=0;ord<numValues;ord++) {
                int id = ids[ord];
                if (missing.get(id) == false && deleted.get(id) == false && matches(bytesPerDim, lower, includeLower, upper, includeUpper, docValues[ord])) {
                  expected.set(id);
                }
              }

              int failCount = 0;
              for(int docID=0;docID<r.maxDoc();docID++) {
                int id = (int) docIDToID.get(docID);
                if (hits.get(docID) != expected.get(id)) {
                  System.out.println("FAIL: iter=" + iter + " id=" + id + " docID=" + docID + " expected=" + expected.get(id) + " but got " + hits.get(docID) + " deleted?=" + deleted.get(id) + " missing?=" + missing.get(id));
                  for(int dim=0;dim<numDims;dim++) {
                    System.out.println("  dim=" + dim + " range: " + bytesToString(lower[dim]) + " (inclusive?=" + includeLower[dim] + ") TO " + bytesToString(upper[dim]) + " (inclusive?=" + includeUpper[dim] + ")");
                    failCount++;
                  }
                }
              }
              if (failCount != 0) {
                fail(failCount + " hits were wrong");
              }
            }
          }
        };
      thread.setName("T" + i);
      thread.start();
      threads.add(thread);
    }
    startingGun.countDown();
    for(Thread thread : threads) {
      thread.join();
    }
    IOUtils.close(r, dir);
  }

  private static boolean matches(Long lower, boolean includeLower, Long upper, boolean includeUpper, long value) {
    if (includeLower == false && lower != null) {
      if (lower == Long.MAX_VALUE) {
        return false;
      }
      lower++;
    }
    if (includeUpper == false && upper != null) {
      if (upper == Long.MIN_VALUE) {
        return false;
      }
      upper--;
    }

    return (lower == null || value >= lower) && (upper == null || value <= upper);
  }

  static String bytesToString(byte[] bytes) {
    if (bytes == null) {
      return "null";
    }
    return new BytesRef(bytes).toString();
  }

  private static boolean matches(int bytesPerDim, byte[][] lower, boolean[] includeLower, byte[][] upper, boolean[] includeUpper, byte[][] value) {
    int numDims = lower.length;
    for(int dim=0;dim<numDims;dim++) {
      int cmp;
      if (lower[dim] == null) {
        cmp = 1;
      } else {
        cmp = NumericUtils.compare(bytesPerDim, value[dim], 0, lower[dim], 0);
      }

      if (cmp < 0 || (cmp == 0 && includeLower[dim] == false)) {
        // Value is below the lower bound, on this dim
        return false;
      }

      if (upper[dim] == null) {
        cmp = -1;
      } else {
        cmp = NumericUtils.compare(bytesPerDim, value[dim], 0, upper[dim], 0);
      }

      if (cmp > 0 || (cmp == 0 && includeUpper[dim] == false)) {
        // Value is above the upper bound, on this dim
        return false;
      }
    }

    return true;
  }

  private static Long randomValue(boolean allowNull) {
    if (valueRange == 0) {
      if (allowNull && random().nextInt(10) == 1) {
        return null;
      } else {
        return random().nextLong();
      }
    } else {
      return valueMid + TestUtil.nextInt(random(), -valueRange, valueRange);
    }
  }
  
  public void testMinMaxLong() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", Long.MIN_VALUE));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new LongPoint("value", Long.MAX_VALUE));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    IndexSearcher s = newSearcher(r);

    assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, 0L, true)));
    assertEquals(1, s.count(LongPoint.newRangeQuery("value", 0L, true, Long.MAX_VALUE, true)));
    assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, Long.MAX_VALUE, true)));

    IOUtils.close(r, w, dir);
  }

  private static byte[] toUTF8(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  // Right zero pads:
  private static byte[] toUTF8(String s, int length) {
    byte[] bytes = s.getBytes(StandardCharsets.UTF_8);
    if (length < bytes.length) {
      throw new IllegalArgumentException("length=" + length + " but string's UTF8 bytes has length=" + bytes.length);
    }
    byte[] result = new byte[length];
    System.arraycopy(bytes, 0, result, 0, bytes.length);
    return result;
  }

  public void testBasicSortedSet() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new BinaryPoint("value", toUTF8("abc")));
    w.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryPoint("value", toUTF8("def")));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    IndexSearcher s = newSearcher(r);

    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("aaa"),
        true,
        toUTF8("bbb"),
        true)));
    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("c", 3),
        true,
        toUTF8("e", 3),
        true)));
    assertEquals(2, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("a", 3),
        true,
        toUTF8("z", 3),
        true)));
    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        null,
        true,
        toUTF8("abc"),
        true)));
    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("a", 3),
        true,
        toUTF8("abc"),
        true)));
    assertEquals(0, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("a", 3),
        true,
        toUTF8("abc"),
        false)));
    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("def"),
        true,
        null,
        false)));
    assertEquals(1, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8(("def")),
        true,
        toUTF8("z", 3),
        true)));
    assertEquals(0, s.count(BinaryPoint.newRangeQuery("value",
        toUTF8("def"),
        false,
        toUTF8("z", 3),
        true)));

    IOUtils.close(r, w, dir);
  }

  public void testLongMinMaxNumeric() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", Long.MIN_VALUE));
    w.addDocument(doc);
    doc = new Document();
    doc.add(new LongPoint("value", Long.MAX_VALUE));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    IndexSearcher s = newSearcher(r);

    assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, Long.MAX_VALUE, true)));
    assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, Long.MAX_VALUE, false)));
    assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, false, Long.MAX_VALUE, true)));
    assertEquals(0, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, false, Long.MAX_VALUE, false)));

    assertEquals(2, s.count(BinaryPoint.newRangeQuery("value", (byte[]) null, true, null, true)));

    IOUtils.close(r, w, dir);
  }

  public void testLongMinMaxSortedSet() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", Long.MIN_VALUE));
    w.addDocument(doc);
    doc = new Document();
    doc.add(new LongPoint("value", Long.MAX_VALUE));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    // We can't wrap with "exotic" readers because the query must see the RangeTreeDVFormat:
    IndexSearcher s = newSearcher(r, false);

    assertEquals(2, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, Long.MAX_VALUE, true)));
    assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, true, Long.MAX_VALUE, false)));
    assertEquals(1, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, false, Long.MAX_VALUE, true)));
    assertEquals(0, s.count(LongPoint.newRangeQuery("value", Long.MIN_VALUE, false, Long.MAX_VALUE, false)));

    assertEquals(2, s.count(LongPoint.newRangeQuery("value", (Long) null, true, null, true)));

    IOUtils.close(r, w, dir);
  }

  public void testSortedSetNoOrdsMatch() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new BinaryPoint("value", toUTF8("a")));
    w.addDocument(doc);
    doc = new Document();
    doc.add(new BinaryPoint("value", toUTF8("z")));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    IndexSearcher s = newSearcher(r);
    assertEquals(0, s.count(BinaryPoint.newRangeQuery("value", toUTF8("m"), true, toUTF8("n"), false)));

    assertEquals(2, s.count(BinaryPoint.newRangeQuery("value", (byte[]) null, true, null, true)));

    IOUtils.close(r, w, dir);
  }

  public void testNumericNoValuesMatch() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new SortedNumericDocValuesField("value", 17));
    w.addDocument(doc);
    doc = new Document();
    doc.add(new SortedNumericDocValuesField("value", 22));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    IndexSearcher s = new IndexSearcher(r);
    assertEquals(0, s.count(LongPoint.newRangeQuery("value", 17L, true, 13L, false)));

    IOUtils.close(r, w, dir);
  }

  public void testNoDocs() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    w.addDocument(new Document());

    IndexReader r = w.getReader();

    IndexSearcher s = newSearcher(r);
    assertEquals(0, s.count(LongPoint.newRangeQuery("value", 17L, true, 13L, false)));

    IOUtils.close(r, w, dir);
  }

  public void testWrongNumDims() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", Long.MIN_VALUE));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    // no wrapping, else the exc might happen in executor thread:
    IndexSearcher s = new IndexSearcher(r);
    byte[][] point = new byte[2][];
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      s.count(BinaryPoint.newMultiRangeQuery("value", point, new boolean[] {true, true}, point, new boolean[] {true, true}));
    });
    assertEquals("field=\"value\" was indexed with numDims=1 but this query has numDims=2", expected.getMessage());

    IOUtils.close(r, w, dir);
  }

  /** ensure good exception when boolean[]s for inclusive have wrong length */
  public void testWrongNumBooleans() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", 1L, 2L));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    // no wrapping, else the exc might happen in executor thread:
    IndexSearcher s = new IndexSearcher(r);
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      s.count(LongPoint.newMultiRangeQuery("value", new Long[] { 1L, 2L }, new boolean[] {true}, new Long[] { 1L, 2L }, new boolean[] {true, true}));
    });
    assertEquals("lowerInclusive has length=1 but expected=2", expected.getMessage());

    expected = expectThrows(IllegalArgumentException.class, () -> {
      s.count(LongPoint.newMultiRangeQuery("value", new Long[] { 1L, 2L }, new boolean[] {true, true}, new Long[] { 1L, 2L }, new boolean[] {true}));
    });
    assertEquals("upperInclusive has length=1 but expected=2", expected.getMessage());

    IOUtils.close(r, w, dir);
  }

  public void testWrongNumBytes() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);
    Document doc = new Document();
    doc.add(new LongPoint("value", Long.MIN_VALUE));
    w.addDocument(doc);

    IndexReader r = w.getReader();

    // no wrapping, else the exc might happen in executor thread:
    IndexSearcher s = new IndexSearcher(r);
    byte[][] point = new byte[1][];
    point[0] = new byte[10];
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class, () -> {
      s.count(BinaryPoint.newMultiRangeQuery("value", point, new boolean[] {true}, point, new boolean[] {true}));
    });
    assertEquals("field=\"value\" was indexed with bytesPerDim=8 but this query has bytesPerDim=10", expected.getMessage());

    IOUtils.close(r, w, dir);
  }

  public void testAllPointDocsWereDeletedAndThenMergedAgain() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);
    Document doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new LongPoint("value", 0L));
    w.addDocument(doc);

    // Add document that won't be deleted to avoid IW dropping
    // segment below since it's 100% deleted:
    w.addDocument(new Document());
    w.commit();

    // Need another segment so we invoke BKDWriter.merge
    doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new LongPoint("value", 0L));
    w.addDocument(doc);
    w.addDocument(new Document());

    w.deleteDocuments(new Term("id", "0"));
    w.forceMerge(1);

    doc = new Document();
    doc.add(new StringField("id", "0", Field.Store.NO));
    doc.add(new LongPoint("value", 0L));
    w.addDocument(doc);
    w.addDocument(new Document());

    w.deleteDocuments(new Term("id", "0"));
    w.forceMerge(1);

    IOUtils.close(w, dir);
  }

  private static Codec getCodec() {
    if (Codec.getDefault().getName().equals("Lucene60")) {
      int maxPointsInLeafNode = TestUtil.nextInt(random(), 16, 2048);
      double maxMBSortInHeap = 4.0 + (3*random().nextDouble());
      if (VERBOSE) {
        System.out.println("TEST: using Lucene60PointFormat with maxPointsInLeafNode=" + maxPointsInLeafNode + " and maxMBSortInHeap=" + maxMBSortInHeap);
      }

      return new FilterCodec("Lucene60", Codec.getDefault()) {
        @Override
        public PointFormat pointFormat() {
          return new PointFormat() {
            @Override
            public PointWriter fieldsWriter(SegmentWriteState writeState) throws IOException {
              return new Lucene60PointWriter(writeState, maxPointsInLeafNode, maxMBSortInHeap);
            }

            @Override
            public PointReader fieldsReader(SegmentReadState readState) throws IOException {
              return new Lucene60PointReader(readState);
            }
          };
        }
      };
    } else {
      return Codec.getDefault();
    }
  }

  public void testExactPoints() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new LongPoint("long", 5L));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new IntPoint("int", 42));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new FloatPoint("float", 2.0f));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new DoublePoint("double", 1.0));
    w.addDocument(doc);

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(1, s.count(IntPoint.newExactQuery("int", 42)));
    assertEquals(0, s.count(IntPoint.newExactQuery("int", 41)));

    assertEquals(1, s.count(LongPoint.newExactQuery("long", 5L)));
    assertEquals(0, s.count(LongPoint.newExactQuery("long", -1L)));

    assertEquals(1, s.count(FloatPoint.newExactQuery("float", 2.0f)));
    assertEquals(0, s.count(FloatPoint.newExactQuery("float", 1.0f)));

    assertEquals(1, s.count(DoublePoint.newExactQuery("double", 1.0)));
    assertEquals(0, s.count(DoublePoint.newExactQuery("double", 2.0)));
    w.close();
    r.close();
    dir.close();
  }

  public void testToString() throws Exception {
    
    // ints
    assertEquals("field:[1 TO 2}", IntPoint.newRangeQuery("field", 1, true, 2, false).toString());
    assertEquals("field:{-2 TO 1]", IntPoint.newRangeQuery("field", -2, false, 1, true).toString());
    assertEquals("field:[* TO 2}", IntPoint.newRangeQuery("field", null, true, 2, false).toString());

    // longs
    assertEquals("field:[1099511627776 TO 2199023255552}", LongPoint.newRangeQuery("field", 1L<<40, true, 1L<<41, false).toString());
    assertEquals("field:{-5 TO 6]", LongPoint.newRangeQuery("field", -5L, false, 6L, true).toString());
    assertEquals("field:[* TO 2}", LongPoint.newRangeQuery("field", null, true, 2L, false).toString());
    
    // floats
    assertEquals("field:[1.3 TO 2.5}", FloatPoint.newRangeQuery("field", 1.3F, true, 2.5F, false).toString());
    assertEquals("field:{-2.9 TO 1.0]", FloatPoint.newRangeQuery("field", -2.9F, false, 1.0F, true).toString());
    assertEquals("field:{-2.9 TO *]", FloatPoint.newRangeQuery("field", -2.9F, false, null, true).toString());
    
    // doubles
    assertEquals("field:[1.3 TO 2.5}", DoublePoint.newRangeQuery("field", 1.3, true, 2.5, false).toString());
    assertEquals("field:{-2.9 TO 1.0]", DoublePoint.newRangeQuery("field", -2.9, false, 1.0, true).toString());
    assertEquals("field:{-2.9 TO *]", DoublePoint.newRangeQuery("field", -2.9, false, null, true).toString());
    
    // n-dimensional double
    assertEquals("field:[1.3 TO 2.5},{-2.9 TO 1.0]", DoublePoint.newMultiRangeQuery("field", 
                                                                      new Double[] { 1.3, -2.9 }, 
                                                                      new boolean[] { true, false }, 
                                                                      new Double[] { 2.5, 1.0 },
                                                                      new boolean[] { false, true }).toString());

  }

  private int[] toArray(Set<Integer> valuesSet) {
    int[] values = new int[valuesSet.size()];
    int upto = 0;
    for(Integer value : valuesSet) {
      values[upto++] = value;
    }
    return values;
  }

  private static int randomIntValue(Integer min, Integer max) {
    if (min == null) {
      return random().nextInt();
    } else {
      return TestUtil.nextInt(random(), min, max);
    }
  }

  public void testRandomPointInSetQuery() throws Exception {

    boolean useNarrowRange = random().nextBoolean();
    final Integer valueMin;
    final Integer valueMax;
    int numValues;
    if (useNarrowRange) {
      int gap = random().nextInt(100);
      valueMin = random().nextInt(Integer.MAX_VALUE-gap);
      valueMax = valueMin + gap;
      numValues = TestUtil.nextInt(random(), 1, gap+1);
    } else {
      valueMin = null;
      valueMax = null;
      numValues = TestUtil.nextInt(random(), 1, 100);
    }
    final Set<Integer> valuesSet = new HashSet<>();
    while (valuesSet.size() < numValues) {
      valuesSet.add(randomIntValue(valueMin, valueMax));
    }
    int[] values = toArray(valuesSet);
    int numDocs = TestUtil.nextInt(random(), 1, 10000);

    if (VERBOSE) {
      System.out.println("TEST: numValues=" + numValues + " numDocs=" + numDocs);
    }

    Directory dir;
    if (numDocs > 100000) {
      dir = newFSDirectory(createTempDir("TestPointQueries"));
    } else {
      dir = newDirectory();
    }

    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    RandomIndexWriter w = new RandomIndexWriter(random(), dir, iwc);

    int[] docValues = new int[numDocs];
    for(int i=0;i<numDocs;i++) {
      int x = values[random().nextInt(values.length)];
      Document doc = new Document();
      doc.add(new IntPoint("int", x));
      docValues[i] = x;
      w.addDocument(doc);
    }

    if (random().nextBoolean()) {
      if (VERBOSE) {
        System.out.println("  forceMerge(1)");
      }
      w.forceMerge(1);
    }
    final IndexReader r = w.getReader();
    w.close();

    IndexSearcher s = newSearcher(r);

    int numThreads = TestUtil.nextInt(random(), 2, 5);

    if (VERBOSE) {
      System.out.println("TEST: use " + numThreads + " query threads; searcher=" + s);
    }

    List<Thread> threads = new ArrayList<>();
    final int iters = atLeast(100);

    final CountDownLatch startingGun = new CountDownLatch(1);
    final AtomicBoolean failed = new AtomicBoolean();

    for(int i=0;i<numThreads;i++) {
      Thread thread = new Thread() {
          @Override
          public void run() {
            try {
              _run();
            } catch (Exception e) {
              failed.set(true);
              throw new RuntimeException(e);
            }
          }

          private void _run() throws Exception {
            startingGun.await();

            for (int iter=0;iter<iters && failed.get() == false;iter++) {

              int numValidValuesToQuery = random().nextInt(values.length);

              Set<Integer> valuesToQuery = new HashSet<>();
              while (valuesToQuery.size() < numValidValuesToQuery) {
                valuesToQuery.add(values[random().nextInt(values.length)]);
              }

              int numExtraValuesToQuery = random().nextInt(20);
              while (valuesToQuery.size() < numValidValuesToQuery + numExtraValuesToQuery) {
                valuesToQuery.add(random().nextInt());
              }

              int expectedCount = 0;
              for(int value : docValues) {
                if (valuesToQuery.contains(value)) {
                  expectedCount++;
                }
              }

              if (VERBOSE) {
                System.out.println("TEST: thread=" + Thread.currentThread() + " values=" + valuesToQuery + " expectedCount=" + expectedCount);
              }

              assertEquals(expectedCount, s.count(IntPoint.newSetQuery("int", toArray(valuesToQuery))));
            }
          }
        };
      thread.setName("T" + i);
      thread.start();
      threads.add(thread);
    }
    startingGun.countDown();
    for(Thread thread : threads) {
      thread.join();
    }
    IOUtils.close(r, dir);
  }

  // TODO: in the future, if there is demand for real usage, we can "graduate" this test-only query factory as IntPoint.newMultiSetQuery or
  // something (and same for other XXXPoint classes):
  private static Query newMultiDimIntSetQuery(String field, final int numDims, int... valuesIn) throws IOException {
    if (valuesIn.length % numDims != 0) {
      throw new IllegalArgumentException("incongruent number of values: valuesIn.length=" + valuesIn.length + " but numDims=" + numDims);
    }

    // Pack all values:
    byte[][] packedValues = new byte[valuesIn.length / numDims][];
    for(int i=0;i<packedValues.length;i++) {
      byte[] packedValue = new byte[numDims * Integer.BYTES];
      packedValues[i] = packedValue;
      for(int dim=0;dim<numDims;dim++) {
        IntPoint.encodeDimension(valuesIn[i*numDims+dim], packedValue, dim*Integer.BYTES);
      }
    }

    // Sort:
    Arrays.sort(packedValues,
                new Comparator<byte[]>() {
                  @Override
                  public int compare(byte[] a, byte[] b) {
                    return StringHelper.compare(a.length, a, 0, b, 0);
                  }
                });

    final BytesRef value = new BytesRef();
    value.length = numDims * Integer.BYTES;

    return new PointInSetQuery(field,
                               numDims,
                               Integer.BYTES,
                               new BytesRefIterator() {
                                 int upto;
                                 @Override
                                 public BytesRef next() {
                                   if (upto >= packedValues.length) {
                                     return null;
                                   }
                                   value.bytes = packedValues[upto];
                                   upto++;
                                   return value;
                                 }
                               }) {
      @Override
      protected String toString(byte[] value) {
        assert value.length == numDims * Integer.BYTES;
        StringBuilder sb = new StringBuilder();
        for(int dim=0;dim<numDims;dim++) {
          if (dim > 0) {
            sb.append(',');
          }
          sb.append(Integer.toString(IntPoint.decodeDimension(value, dim*Integer.BYTES)));
        }

        return sb.toString();
      }
    };
  }

  public void testBasicMultiDimPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new IntPoint("int", 17, 42));
    w.addDocument(doc);
    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);

    assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 17, 41)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 17, 42)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14)));

    w.close();
    r.close();
    dir.close();
  }

  public void testBasicMultiValueMultiDimPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new IntPoint("int", 17, 42));
    doc.add(new IntPoint("int", 34, 79));
    w.addDocument(doc);
    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);

    assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 17, 41)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, 34, 79)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 17, 42)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, -7, -7, 34, 79)));
    assertEquals(1, s.count(newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14)));

    assertEquals("int:{-14,-14 17,42}", newMultiDimIntSetQuery("int", 2, 17, 42, -14, -14).toString());

    w.close();
    r.close();
    dir.close();
  }

  public void testManyEqualValuesMultiDimPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    int zeroCount = 0;
    for(int i=0;i<10000;i++) {
      int x = random().nextInt(2);
      if (x == 0) {
        zeroCount++;
      }
      Document doc = new Document();
      doc.add(new IntPoint("int", x, x));
      w.addDocument(doc);
    }
    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);

    assertEquals(zeroCount, s.count(newMultiDimIntSetQuery("int", 2, 0, 0)));
    assertEquals(10000-zeroCount, s.count(newMultiDimIntSetQuery("int", 2, 1, 1)));
    assertEquals(0, s.count(newMultiDimIntSetQuery("int", 2, 2, 2)));

    w.close();
    r.close();
    dir.close();
  }

  public void testInvalidMultiDimPointInSetQuery() throws Exception {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class,
                                                     () -> {
                                                       newMultiDimIntSetQuery("int", 2, 3, 4, 5);
                                                     });
    assertEquals("incongruent number of values: valuesIn.length=3 but numDims=2", expected.getMessage());
  }

  public void testBasicPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new IntPoint("int", 17));
    doc.add(new LongPoint("long", 17L));
    doc.add(new FloatPoint("float", 17.0f));
    doc.add(new DoublePoint("double", 17.0));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 17}));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new IntPoint("int", 42));
    doc.add(new LongPoint("long", 42L));
    doc.add(new FloatPoint("float", 42.0f));
    doc.add(new DoublePoint("double", 42.0));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 42}));
    w.addDocument(doc);

    doc = new Document();
    doc.add(new IntPoint("int", 97));
    doc.add(new LongPoint("long", 97L));
    doc.add(new FloatPoint("float", 97.0f));
    doc.add(new DoublePoint("double", 97.0));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 97}));
    w.addDocument(doc);

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(0, s.count(IntPoint.newSetQuery("int", 16)));
    assertEquals(1, s.count(IntPoint.newSetQuery("int", 17)));
    assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 97, 42)));
    assertEquals(3, s.count(IntPoint.newSetQuery("int", -7, 17, 42, 97)));
    assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 20, 42, 97)));
    assertEquals(3, s.count(IntPoint.newSetQuery("int", 17, 105, 42, 97)));

    assertEquals(0, s.count(LongPoint.newSetQuery("long", 16)));
    assertEquals(1, s.count(LongPoint.newSetQuery("long", 17)));
    assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 97, 42)));
    assertEquals(3, s.count(LongPoint.newSetQuery("long", -7, 17, 42, 97)));
    assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 20, 42, 97)));
    assertEquals(3, s.count(LongPoint.newSetQuery("long", 17, 105, 42, 97)));

    assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16)));
    assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17)));
    assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17, 97, 42)));
    assertEquals(3, s.count(FloatPoint.newSetQuery("float", -7, 17, 42, 97)));
    assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17, 20, 42, 97)));
    assertEquals(3, s.count(FloatPoint.newSetQuery("float", 17, 105, 42, 97)));

    assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16)));
    assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17)));
    assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17, 97, 42)));
    assertEquals(3, s.count(DoublePoint.newSetQuery("double", -7, 17, 42, 97)));
    assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17, 20, 42, 97)));
    assertEquals(3, s.count(DoublePoint.newSetQuery("double", 17, 105, 42, 97)));

    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 16})));
    assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17})));
    assertEquals(3, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17}, new byte[] {0, 97}, new byte[] {0, 42})));
    assertEquals(3, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, -7}, new byte[] {0, 17}, new byte[] {0, 42}, new byte[] {0, 97})));
    assertEquals(3, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17}, new byte[] {0, 20}, new byte[] {0, 42}, new byte[] {0, 97})));
    assertEquals(3, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17}, new byte[] {0, 105}, new byte[] {0, 42}, new byte[] {0, 97})));

    w.close();
    r.close();
    dir.close();
  }

  public void testBasicMultiValuedPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new IntPoint("int", 17));
    doc.add(new IntPoint("int", 42));
    doc.add(new LongPoint("long", 17L));
    doc.add(new LongPoint("long", 42L));
    doc.add(new FloatPoint("float", 17.0f));
    doc.add(new FloatPoint("float", 42.0f));
    doc.add(new DoublePoint("double", 17.0));
    doc.add(new DoublePoint("double", 42.0));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 17}));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 42}));
    w.addDocument(doc);

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(0, s.count(IntPoint.newSetQuery("int", 16)));
    assertEquals(1, s.count(IntPoint.newSetQuery("int", 17)));
    assertEquals(1, s.count(IntPoint.newSetQuery("int", 17, 97, 42)));
    assertEquals(1, s.count(IntPoint.newSetQuery("int", -7, 17, 42, 97)));
    assertEquals(0, s.count(IntPoint.newSetQuery("int", 16, 20, 41, 97)));

    assertEquals(0, s.count(LongPoint.newSetQuery("long", 16)));
    assertEquals(1, s.count(LongPoint.newSetQuery("long", 17)));
    assertEquals(1, s.count(LongPoint.newSetQuery("long", 17, 97, 42)));
    assertEquals(1, s.count(LongPoint.newSetQuery("long", -7, 17, 42, 97)));
    assertEquals(0, s.count(LongPoint.newSetQuery("long", 16, 20, 41, 97)));

    assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16)));
    assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17)));
    assertEquals(1, s.count(FloatPoint.newSetQuery("float", 17, 97, 42)));
    assertEquals(1, s.count(FloatPoint.newSetQuery("float", -7, 17, 42, 97)));
    assertEquals(0, s.count(FloatPoint.newSetQuery("float", 16, 20, 41, 97)));

    assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16)));
    assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17)));
    assertEquals(1, s.count(DoublePoint.newSetQuery("double", 17, 97, 42)));
    assertEquals(1, s.count(DoublePoint.newSetQuery("double", -7, 17, 42, 97)));
    assertEquals(0, s.count(DoublePoint.newSetQuery("double", 16, 20, 41, 97)));

    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 16})));
    assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17})));
    assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 17}, new byte[] {0, 97}, new byte[] {0, 42})));
    assertEquals(1, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, -7}, new byte[] {0, 17}, new byte[] {0, 42}, new byte[] {0, 97})));
    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0, 16}, new byte[] {0, 20}, new byte[] {0, 41}, new byte[] {0, 97})));

    w.close();
    r.close();
    dir.close();
  }

  public void testEmptyPointInSetQuery() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    Document doc = new Document();
    doc.add(new IntPoint("int", 17));
    doc.add(new LongPoint("long", 17L));
    doc.add(new FloatPoint("float", 17.0f));
    doc.add(new DoublePoint("double", 17.0));
    doc.add(new BinaryPoint("bytes", new byte[] {0, 17}));
    w.addDocument(doc);

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(0, s.count(IntPoint.newSetQuery("int")));
    assertEquals(0, s.count(LongPoint.newSetQuery("long")));
    assertEquals(0, s.count(FloatPoint.newSetQuery("float")));
    assertEquals(0, s.count(DoublePoint.newSetQuery("double")));
    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes")));

    w.close();
    r.close();
    dir.close();
  }

  public void testPointInSetQueryManyEqualValues() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    int zeroCount = 0;
    for(int i=0;i<10000;i++) {
      int x = random().nextInt(2);
      if (x == 0) {
        zeroCount++;
      }
      Document doc = new Document();
      doc.add(new IntPoint("int", x));
      doc.add(new LongPoint("long", (long) x));
      doc.add(new FloatPoint("float", (float) x));
      doc.add(new DoublePoint("double", (double) x));
      doc.add(new BinaryPoint("bytes", new byte[] {(byte) x}));
      w.addDocument(doc);
    }

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0)));
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0, -7)));
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 7, 0)));
    assertEquals(10000-zeroCount, s.count(IntPoint.newSetQuery("int", 1)));
    assertEquals(0, s.count(IntPoint.newSetQuery("int", 2)));

    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0)));
    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0, -7)));
    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 7, 0)));
    assertEquals(10000-zeroCount, s.count(LongPoint.newSetQuery("long", 1)));
    assertEquals(0, s.count(LongPoint.newSetQuery("long", 2)));

    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0)));
    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0, -7)));
    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 7, 0)));
    assertEquals(10000-zeroCount, s.count(FloatPoint.newSetQuery("float", 1)));
    assertEquals(0, s.count(FloatPoint.newSetQuery("float", 2)));

    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0)));
    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0, -7)));
    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 7, 0)));
    assertEquals(10000-zeroCount, s.count(DoublePoint.newSetQuery("double", 1)));
    assertEquals(0, s.count(DoublePoint.newSetQuery("double", 2)));

    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0})));
    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0}, new byte[] {-7})));
    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {7}, new byte[] {0})));
    assertEquals(10000-zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {1})));
    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {2})));

    w.close();
    r.close();
    dir.close();
  }

  public void testPointInSetQueryManyEqualValuesWithBigGap() throws Exception {
    Directory dir = newDirectory();
    IndexWriterConfig iwc = newIndexWriterConfig();
    iwc.setCodec(getCodec());
    IndexWriter w = new IndexWriter(dir, iwc);

    int zeroCount = 0;
    for(int i=0;i<10000;i++) {
      int x = 200 * random().nextInt(2);
      if (x == 0) {
        zeroCount++;
      }
      Document doc = new Document();
      doc.add(new IntPoint("int", x));
      doc.add(new LongPoint("long", (long) x));
      doc.add(new FloatPoint("float", (float) x));
      doc.add(new DoublePoint("double", (double) x));
      doc.add(new BinaryPoint("bytes", new byte[] {(byte) x}));
      w.addDocument(doc);
    }

    IndexReader r = DirectoryReader.open(w);
    IndexSearcher s = newSearcher(r);
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0)));
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 0, -7)));
    assertEquals(zeroCount, s.count(IntPoint.newSetQuery("int", 7, 0)));
    assertEquals(10000-zeroCount, s.count(IntPoint.newSetQuery("int", 200)));
    assertEquals(0, s.count(IntPoint.newSetQuery("int", 2)));

    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0)));
    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 0, -7)));
    assertEquals(zeroCount, s.count(LongPoint.newSetQuery("long", 7, 0)));
    assertEquals(10000-zeroCount, s.count(LongPoint.newSetQuery("long", 200)));
    assertEquals(0, s.count(LongPoint.newSetQuery("long", 2)));

    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0)));
    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 0, -7)));
    assertEquals(zeroCount, s.count(FloatPoint.newSetQuery("float", 7, 0)));
    assertEquals(10000-zeroCount, s.count(FloatPoint.newSetQuery("float", 200)));
    assertEquals(0, s.count(FloatPoint.newSetQuery("float", 2)));

    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0)));
    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 0, -7)));
    assertEquals(zeroCount, s.count(DoublePoint.newSetQuery("double", 7, 0)));
    assertEquals(10000-zeroCount, s.count(DoublePoint.newSetQuery("double", 200)));
    assertEquals(0, s.count(DoublePoint.newSetQuery("double", 2)));

    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0})));
    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {0}, new byte[] {-7})));
    assertEquals(zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {7}, new byte[] {0})));
    assertEquals(10000-zeroCount, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {(byte) 200})));
    assertEquals(0, s.count(BinaryPoint.newSetQuery("bytes", new byte[] {2})));

    w.close();
    r.close();
    dir.close();
  }

  public void testInvalidPointInSetQuery() throws Exception {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class,
                                                     () -> {
                                                       new PointInSetQuery("foo", 3, 4,
                                                                           new BytesRefIterator() {
                                                                             @Override
                                                                             public BytesRef next() {
                                                                               return new BytesRef(new byte[3]);
                                                                             }
                                                                           });
                                                     });
    assertEquals("packed point length should be 12 but got 3; field=\"foo\" numDims=3 bytesPerDim=4", expected.getMessage());
  }

  public void testInvalidPointInSetBinaryQuery() throws Exception {
    IllegalArgumentException expected = expectThrows(IllegalArgumentException.class,
                                                     () -> {
                                                       BinaryPoint.newSetQuery("bytes", new byte[] {2}, new byte[0]);
                                                     });
    assertEquals("all byte[] must be the same length, but saw 1 and 0", expected.getMessage());
  }

  public void testPointInSetQueryToString() throws Exception {
    // int
    assertEquals("int:{-42 18}", IntPoint.newSetQuery("int", -42, 18).toString());

    // long
    assertEquals("long:{-42 18}", LongPoint.newSetQuery("long", -42L, 18L).toString());

    // float
    assertEquals("float:{-42.0 18.0}", FloatPoint.newSetQuery("float", -42.0f, 18.0f).toString());

    // double
    assertEquals("double:{-42.0 18.0}", DoublePoint.newSetQuery("double", -42.0, 18.0).toString());

    // binary
    assertEquals("bytes:{[12] [2a]}", BinaryPoint.newSetQuery("bytes", new byte[] {42}, new byte[] {18}).toString());
  }
}
