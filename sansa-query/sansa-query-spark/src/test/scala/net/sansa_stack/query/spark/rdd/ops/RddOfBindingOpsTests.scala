package net.sansa_stack.query.spark.rdd.ops

import java.util

import com.holdenkarau.spark.testing.DataFrameSuiteBase
import net.sansa_stack.query.spark.ops.rdd.RddOfBindingOps
import org.aksw.jena_sparql_api.utils.{ResultSetUtils, Vars}
import org.apache.jena.query.{Query, QueryExecutionFactory, SortCondition}
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.sparql.core.VarExprList
import org.apache.jena.sparql.engine.binding.Binding
import org.apache.jena.sparql.expr.aggregate.AggCount
import org.apache.jena.sparql.expr._
import org.apache.spark.SparkConf
import org.apache.spark.rdd.RDD
import org.scalatest.FunSuite

import scala.jdk.CollectionConverters.asScalaIteratorConverter

class RddOfBindingOpsTests extends FunSuite with DataFrameSuiteBase {

  // JenaSystem.init

  override def conf(): SparkConf = {
    val conf = super.conf
    conf
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
      .set("spark.kryo.registrator", String.join(", ",
        "net.sansa_stack.rdf.spark.io.JenaKryoRegistrator"))
    conf
  }


  def createTestRdd(): RDD[Binding] = {
    val model = RDFDataMgr.loadModel("rdf.nt")
    val bindings: Seq[Binding] = ResultSetUtils.toIteratorBinding(
      QueryExecutionFactory.create("SELECT * { ?s ?p ?o }", model).execSelect()).asScala.toSeq

    val rdd: RDD[Binding] = sc.parallelize(bindings)
    rdd
  }

  test("projection of RDD[Binding] should match expected result") {
    val rdd = createTestRdd()
    for (elem <- RddOfBindingOps.project(rdd, java.util.Arrays.asList(Vars.s)).collect()) {
      println(elem)
    }
  }

  test("extend of RDD[Binding] should match expected result") {
    val rdd = createTestRdd()

    val vel: VarExprList = new VarExprList
    vel.add(Vars.x, new E_StrConcat(
      new ExprList(util.Arrays.asList[Expr](NodeValue.makeString("string: "), new ExprVar(Vars.o)))))

    for (elem <- RddOfBindingOps.extend(rdd, vel).collect()) {
      println(elem)
    }
  }

  test("order of RDD[Binding] should match expected result") {
    val rdd = createTestRdd()

    val sortConditions = util.Arrays.asList(new SortCondition(new ExprVar(Vars.o), Query.ORDER_ASCENDING))

    for (elem <- RddOfBindingOps.order(rdd, sortConditions).collect()) {
      println(elem)
    }
  }

  test("group of RDD[Binding] should match expected result") {
    val rdd = createTestRdd()

    val groupVars = new VarExprList
    groupVars.add(Vars.s)

    val exprAggs = new util.ArrayList[ExprAggregator]()
    exprAggs.add(new ExprAggregator(Vars.x, new AggCount))

    for (elem <- RddOfBindingOps.group(rdd, groupVars, exprAggs).collect()) {
      println(elem)
    }
  }


}