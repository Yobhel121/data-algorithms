package org.dataalgorithms.chap17.sparkwithlambda;

// STEP-0: import required classes and interfaces

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import org.dataalgorithms.util.SparkUtil;
import scala.Tuple2;

import java.util.*;

//
//
//

/**
 * This class provides K-mer counting functionality.
 * 
 * Kmer counting for a given K and N.
 * K: to find K-mers
 * N: to find top-N
 * 
 * A kmer or k-mer is a short DNA sequence consisting of a fixed 
 * number (K) of bases. The value of k is usually divisible by 4 
 * so that a kmer can fit compactly into a basevector object. 
 * Typical values include 12, 20, 24, 36, and 48; kmers of these 
 * sizes are referred to as 12-mers, 20-mers, and so forth.
 *
 * @author Mahmoud Parsian
 *
 */
public class Kmer {

    // hdfs://cm01:8020/test/input 3 4
   public static void main(String[] args) throws Exception {
      // STEP-1: handle input parameters
      if (args.length < 3) {
         System.err.println("Usage: Kmer <fastq-file> <K> <N>");
         System.exit(1);
      }
      final String fastqFileName =  args[0];
      final int K =  Integer.parseInt(args[1]); // to find K-mers
      final int N =  Integer.parseInt(args[2]); // to find top-N

      // STEP-2: create a Spark context object
      JavaSparkContext ctx = SparkUtil.createJavaSparkContext("local","kmer");
      
      // broadcast K and N as global shared objects,
      // which can be accessed from all cluster nodes
      final Broadcast<Integer> broadcastK = ctx.broadcast(K);
      final Broadcast<Integer> broadcastN = ctx.broadcast(N);
       
      // STEP-3: read all transactions from HDFS and create the first RDD                   
      JavaRDD<String> records = ctx.textFile(fastqFileName, 1);
      records.saveAsTextFile("/kmers/output/1");

      // JavaRDD<T> filter(Function<T,Boolean> f)
      // Return a new RDD containing only the elements that satisfy a predicate.
      JavaRDD<String> filteredRDD = records.filter((String record) -> {
          String firstChar = record.substring(0,1);
          if ( firstChar.equals("@") ||
               firstChar.equals("+") ||
               firstChar.equals(";") ||
               firstChar.equals("!") ||
               firstChar.equals("~") ) {
              return false; // do not return these records
          }
          else {
              return true;
          }
      });
      
      // STEP-4: generate K-mers
      // PairFlatMapFunction<T, K, V>     
      // T => Iterable<Tuple2<K, V>>
      JavaPairRDD<String,Integer> kmers = filteredRDD.flatMapToPair((String sequence) -> {
          int K1 = broadcastK.value();
          List<Tuple2<String,Integer>> list = new ArrayList<Tuple2<String,Integer>>();
          for (int i = 0; i < sequence.length() - K1 + 1; i++) {
              String kmer = sequence.substring(i, K1 + i);
              list.add(new Tuple2<String,Integer>(kmer, 1));
          }
          return list.iterator();
      });    
      kmers.saveAsTextFile("/kmers/output/2");
    
      // STEP-5: combine/reduce frequent kmers
      JavaPairRDD<String, Integer> kmersGrouped = 
              kmers.reduceByKey((Integer i1, Integer i2) -> i1 + i2);    
      kmersGrouped.saveAsTextFile("/kmers/output/3");
    
      // now, we have: (K=kmer,V=frequency)
      // next step is find the top-N kmers
      // create a local top-N
      JavaRDD<SortedMap<Integer, String>> partitions = 
              kmersGrouped.mapPartitions((Iterator<Tuple2<String,Integer>> iter) -> {
          int N1 = broadcastN.value();
          SortedMap<Integer, String> topN = new TreeMap<Integer, String>();
          while (iter.hasNext()) {
              Tuple2<String,Integer> tuple = iter.next();
              String kmer = tuple._1;
              int frequency = tuple._2;
              topN.put(frequency, kmer);
              // keep only top N
              if (topN.size() > N1) {
                  topN.remove(topN.firstKey());  
              }
          }
          System.out.println("topN="+topN);
          return Collections.singletonList(topN).iterator();
      });

      // now collect all topN from all partitions 
      // and find topN from all partitions
      SortedMap<Integer, String> finaltopN = new TreeMap<Integer, String>();
      List<SortedMap<Integer, String>> alltopN = partitions.collect();
      for (SortedMap<Integer, String> localtopN : alltopN) {
          // frequency = tuple._1
          // kmer = tuple._2
          for (Map.Entry<Integer, String> entry : localtopN.entrySet()) {
              finaltopN.put(entry.getKey(), entry.getValue());
              // keep only top N 
              if (finaltopN.size() > N) {
                 finaltopN.remove(finaltopN.firstKey());
              }
          }
      }
    
      // emit final topN descending
      System.out.println("=== top " + N + " ===");
      List<Integer> frequencies = new ArrayList<Integer>(finaltopN.keySet());
      for(int i = frequencies.size()-1; i>=0; i--) {
          System.out.println(frequencies.get(i) + "\t" + finaltopN.get(frequencies.get(i)));
      } 
      
      
      //for (Map.Entry<Integer, String> entry : finaltopN.entrySet()) {
      //   System.out.println(entry.getKey() + "--" + entry.getValue());
      //}

      // done
      ctx.close();
      System.exit(0);
   }
}