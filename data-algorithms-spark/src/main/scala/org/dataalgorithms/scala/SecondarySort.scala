package org.dataalgorithms.scala

import org.apache.spark.{SparkConf, SparkContext}

/**
 * Spark/Scala solution to secondary sort
 *
 * @author Gaurav Bhardwaj (gauravbhardwajemail@gmail.com)
 * 
 * @editor Mahmoud Parsian (mahmoud.parsian@yahoo.com)
 *
 */
object SecondarySort {

  // 1 hdfs://cm01:8020/test/input/ hdfs://cm01:8020/test/ouput/
  def main(args: Array[String]): Unit = {
    if (args.length != 3) {
      println("Usage <number-of-partitions> <input-path> <output-path>")
      sys.exit(1)
    }

    val partitions = args(0).toInt
    val inputPath = args(1)
    val outputPath = args(2)

    val config = new SparkConf
    config.setAppName("SecondarySort").setMaster("local")
    val sc = new SparkContext(config)

    val input = sc.textFile(inputPath)

    //------------------------------------------------
    // each input line/record has the following format:
    // <id><,><time><,><value>
    //-------------------------------------------------
    val valueToKey = input.map(x => {
      val line = x.split(",")
      ((line(0) + "-" + line(1), line(2).toInt), line(2).toInt)
    })    

   /* implicit def tupleOrderingDesc = new Ordering[Tuple2[String, Int]] {
      override def compare(x: Tuple2[String, Int], y: Tuple2[String, Int]): Int = {
        if (y._1.compare(x._1) == 0) y._2.compare(x._2)
        else y._1.compare(x._1)
      }
    }*/

    val sorted = valueToKey.repartitionAndSortWithinPartitions(new CustomPartitioner(partitions))

    val result = sorted.map {
      case (k, v) => (k._1, v)
    }

    result.saveAsTextFile(outputPath)

    // done
    sc.stop()
  }
}
