package net.sansa_stack.rdf.spark.qualityassessment.metrics

import org.scalatest.FunSuite
import com.holdenkarau.spark.testing.DataFrameSuiteBase
import org.apache.jena.riot.Lang
import net.sansa_stack.rdf.spark.io._
import net.sansa_stack.rdf.spark.qualityassessment._

class ConcisenessTests extends FunSuite with DataFrameSuiteBase {
  
  test("assessing the extensional conciseness should result in value 0.4056603773584906") {

    val path = getClass.getResource("/rdf.nt").getPath
    val lang: Lang = Lang.NTRIPLES

    val triples = spark.rdf(lang)(path)

    val value = triples.assessExtensionalConciseness()
    assert(value == 0.4056603773584906)
  }
}