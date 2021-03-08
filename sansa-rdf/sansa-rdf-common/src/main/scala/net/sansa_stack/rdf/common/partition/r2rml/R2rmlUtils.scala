package net.sansa_stack.rdf.common.partition.r2rml

import net.sansa_stack.rdf.common.partition.core.{RdfPartitionStateDefault, RdfPartitioner, TermType}
import net.sansa_stack.rdf.common.partition.utils.SQLUtils
import org.aksw.commons.sql.codec.api.SqlCodec
import org.aksw.commons.sql.codec.util.SqlCodecUtils
import org.aksw.r2rml.jena.arq.lib.R2rmlLib
import org.aksw.r2rml.jena.domain.api._
import org.aksw.r2rml.jena.vocab.RR
import org.aksw.r2rmlx.domain.api.TermMapX
import org.apache.jena.rdf.model.{Model, Property, ResourceFactory}
import org.apache.jena.sparql.core.Var
import org.apache.jena.sparql.expr.ExprVar

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.reflect.runtime.universe.MethodSymbol

object R2rmlUtils {
  implicit def newExprVar(varName: String): ExprVar = new ExprVar(Var.alloc(varName))
  implicit def newExprVar(varId: Int): ExprVar = "_" + varId

  def newExprVar(i: Int, attrNames: List[String]): ExprVar = {
    val attrName = attrNames(i)
    attrName
  }

  /**
   * Transform a sequence of [[RdfPartitionStateDefault]] objects into a sequence of R2RML mappings.
   * If the language handling strategy demands a dedicated column for language tags then the
   * resulting R2RML contains the non-standard 'rr:langColumn' property.
   *
   * FIXME Creating mappings per language tag needs yet to be implemented
   *
   * @param partitioner         The partitioner
   * @param partitionStates     The partition states generated by the partitioner
   * @param outModel            The output model
   * @param explodeLanguageTags If true then a mapping is generated for each language tag listed in the partition state.
   *                            Otherwise a generic language column is introduced
   * @return The sequence of {@link TriplesMap}s added to the output model
   */
  def createR2rmlMappings(partitioner: RdfPartitioner[RdfPartitionStateDefault],
                          partitionStates: Seq[RdfPartitionStateDefault],
                          outModel: Model,
                          explodeLanguageTags: Boolean): Seq[TriplesMap] = {
    partitionStates
      .flatMap(p => createR2rmlMappings(
        partitioner,
        p,
        outModel,
        explodeLanguageTags))
  }

  /**
   * Transform a sequence of [[RdfPartitionStateDefault]] objects into a sequence of R2RML mappings.
   * If the language handling strategy demands a dedicated column for language tags then the
   * resulting R2RML contains the non-standard 'rr:langColumn' property.
   *
   * FIXME Creating mappings per language tag needs yet to be implemented
   *
   * @param partitioner         The partitioner
   * @param partitionStates     The partition states generated by the partitioner
   * @param extractTableName    A function to obtain a table name from the partition state
   * @param sqlCodec          SQL escaping policies for table names, column names, string literals and aliases
   * @param outModel            The output model
   * @param explodeLanguageTags If true then a mapping is generated for each language tag listed in the partition state.
   *                            Otherwise a generic language column is introduced
   * @return The sequence of {@link TriplesMap}s added to the output model
   */
  def createR2rmlMappings(partitioner: RdfPartitioner[RdfPartitionStateDefault],
                          partitionStates: Seq[RdfPartitionStateDefault],
                          extractTableName: RdfPartitionStateDefault => String,
                          tableNameQualifier: Option[String] = None,
                          sqlCodec: SqlCodec,
                          outModel: Model,
                          explodeLanguageTags: Boolean): Seq[TriplesMap] = {
    partitionStates
      .flatMap(p => createR2rmlMappings(
        partitioner,
        p,
        extractTableName,
        tableNameQualifier,
        sqlCodec,
        outModel,
        explodeLanguageTags))
  }

  /**
   * Transform a [[RdfPartitionStateDefault]] into a sequence of R2RML mappings.
   * If the language handling strategy demands a dedicated column for language tags then the
   * resulting R2RML contains the non-standard 'rr:langColumn' property.
   *
   * FIXME Creating mappings per language tag needs yet to be implemented
   *
   * @param partitioner         The partitioner
   * @param partitionState      The partition state generated by the partitioner
   * @param outModel            The output model
   * @param explodeLanguageTags If true then a mapping is generated for each language tag listed in the partition state.
   *                            Otherwise a generic language column is introduced
   * @return The sequence of {@link TriplesMap}s added to the output model
   */
  def createR2rmlMappings(partitioner: RdfPartitioner[RdfPartitionStateDefault],
                          partitionState: RdfPartitionStateDefault,
                          outModel: Model,
                          explodeLanguageTags: Boolean): Seq[TriplesMap] = {
    createR2rmlMappings(
      partitioner,
      partitionState,
      p => SQLUtils.createDefaultTableName(p), // Map the partition to a name
      None,
      SqlCodecUtils.createSqlCodecDefault,
      outModel,
      explodeLanguageTags)
  }

  /**
   * Transform a [[RdfPartitionStateDefault]] into a sequence of R2RML mappings.
   * If the language handling strategy demands a dedicated column for language tags then the
   * resulting R2RML contains the non-standard 'rr:langColumn' property.
   *
   * FIXME Creating mappings per language tag needs yet to be implemented
   *
   * @param partitioner         The partitioner
   * @param partitionState      The partition state generated by the partitioner
   * @param extractTableName    A function to obtain a table name from the partition state
   * @param sqlCodec          SQL escaping policies for table names, column names, string literals and aliases
   * @param outModel            The output model
   * @param explodeLanguageTags If true then a mapping is generated for each language tag listed in the partition state.
   *                            Otherwise a generic language column is introduced
   * @return The sequence of {@link TriplesMap}s added to the output model
   */
  def createR2rmlMappings(partitioner: RdfPartitioner[RdfPartitionStateDefault],
                          partitionState: RdfPartitionStateDefault,
                          extractTableName: RdfPartitionStateDefault => String,
                          tableNameQualifier: Option[String],
                          sqlCodec: SqlCodec,
                          outModel: Model,
                          explodeLanguageTags: Boolean): Seq[TriplesMap] = {

    val p = partitionState // Shorthand
    val t = partitioner.determineLayout(partitionState).schema

    val columnNames = t.members.sorted.collect({ case m: MethodSymbol if m.isCaseAccessor => m.name.toString })
    val encodedColumnNames = columnNames.map(sqlCodec.forColumnName.encode)

    val predicateIri: String = partitionState.predicate
    val tableName = extractTableName(partitionState)

    // consider an optional table name qualifier and prepend it
    val encodedTableName = tableNameQualifier
      .map(tnq => sqlCodec.forSchemaName.encode(tnq) + ".").getOrElse("") +
      sqlCodec.forTableName.encode(tableName)

    // if enabled, create mappings per language tag
    if (explodeLanguageTags && encodedColumnNames.length == 3) {
      val projectedColumns = encodedColumnNames.slice(0, 2)
      val columnsSql = projectedColumns.mkString(", ")
      val langColSql = encodedColumnNames(2)

      // if there is only one language tag, we can omit the SQL query with the FILTER on the lang column
      if (p.languages.size == 1) {
        // TODO put to outer if-else and just add rr:language attribute
        // TODO for this case we wouldn't even need a table with a lang column, as long as the mapping keeps track of the language
        val tm: TriplesMap = outModel.createResource.as(classOf[TriplesMap])
        val pom: PredicateObjectMap = tm.addNewPredicateObjectMap()
        pom.addPredicate(predicateIri)

        // create subject map
        val sm: SubjectMap = tm.getOrSetSubjectMap()
        setTermMapForNode(sm, 0, encodedColumnNames, p.subjectType, "", false)

        // and the object map
        val om: ObjectMap = pom.addNewObjectMap()
        om.setColumn(encodedColumnNames(1))
        if (p.languages.head.trim.nonEmpty) om.setLanguage(p.languages.head)

        tm.getOrSetLogicalTable().asBaseTableOrView().setTableName(encodedTableName)

        Seq(tm)
      } else {
        p.languages.map(lang => {
          val langSql = sqlCodec.forStringLiteral.encode(lang)

          val tm: TriplesMap = outModel.createResource.as(classOf[TriplesMap])

          // create subject map
          val sm: SubjectMap = tm.getOrSetSubjectMap()
          setTermMapForNode(sm, 0, encodedColumnNames, p.subjectType, "", false)

          val pom: PredicateObjectMap = tm.addNewPredicateObjectMap()
          pom.addPredicate(predicateIri)

          val om: ObjectMap = pom.addNewObjectMap()
          om.setColumn(encodedColumnNames(1))
          if (lang.trim.nonEmpty) om.setLanguage(lang)

          tm.getOrSetLogicalTable().asR2rmlView().setSqlQuery(s"SELECT $columnsSql FROM $encodedTableName WHERE $langColSql = $langSql")

          tm
        }).toSeq
      }
    } else {
      val tm: TriplesMap = outModel.createResource.as(classOf[TriplesMap])

      // create subject map
      val sm: SubjectMap = tm.getOrSetSubjectMap()
      setTermMapForNode(sm, 0, encodedColumnNames, p.subjectType, "", false)

      // create predicate-object map
      val pom: PredicateObjectMap = tm.addNewPredicateObjectMap()
      pom.addPredicate(predicateIri)

      // create object map
      val om: ObjectMap = pom.addNewObjectMap()
      setTermMapForNode(om, 1, encodedColumnNames, p.objectType, p.datatype, p.langTagPresent)

      tm.getOrSetLogicalTable().asBaseTableOrView().setTableName(encodedTableName)

      Seq(tm)
    }
  }

  def setTermMapForNode(target: TermMap, offset: Int, attrNames: List[String], termType: Byte, datatype: String, langTagPresent: Boolean): TermMap = {
    // val o = offset + 1
    val o = offset

    val on = newExprVar(o, attrNames)

    termType match {
      // TODO The RR.IRI.inModel(...) is a workaround right now
      case TermType.BLANK => target.setColumn(attrNames(o)).setTermType(RR.BlankNode.inModel(target.getModel))
      case TermType.IRI => target.setColumn(attrNames(o)).setTermType(RR.IRI.inModel(target.getModel))
      case TermType.LITERAL =>
        target.setColumn(attrNames(o))
        if (langTagPresent) {
          target.as(classOf[TermMapX]).setLangColumn(attrNames(o + 1))
        } else {
          target.setDatatype(ResourceFactory.createProperty(datatype))
        }
      // case 2 if(!Option(datatype).getOrElse("").isEmpty) => E_RdfTerm.createTypedLiteral(o, o + 1)
      case _ => throw new RuntimeException("Unhandled case")
    }

    target
  }

  /**
   * Imports the RDF partition states as [[TriplesMap]] from the given RDF data model.
   *
   * @param model the model
   * @return the RDF partition states as [[TriplesMap]]
   */
  def streamTriplesMaps(model: Model): Iterator[TriplesMap] = {
    import collection.JavaConverters._
    R2rmlLib.streamTriplesMaps(model).iterator().asScala
  }

  /**
   * Returns all triples maps for the given predicate.
   *
   * @param predicate the predicate
   * @param model the model
   * @return the [[TriplesMap]]s that use the given predicate
   */
  def triplesMapsForPredicate(predicate: Property, model: Model): Iterator[TriplesMap] = {
    model
      .listResourcesWithProperty(RR.subjectMap).asScala
      .map(_.as(classOf[TriplesMap]))
      .filter(tm =>
        tm.getPredicateObjectMaps.asScala.exists(_.getPredicateMaps.asScala.exists(pm => Option(pm.getConstant).contains(predicate))))
  }

//  /**
//   * Make all table identifiers being qualified with the given database resp. schema name.
//   *
//   * @param database the database schema name
//   * @param model    the R2RML mappings
//   * @return the modified R2RML mappings
//   */
//  def makeQualifiedTableIdentifiers(database: String, model: Model): Model = {
//    streamTriplesMaps(model).foreach(tm => {
//      val lt = tm.getOrSetLogicalTable()
//      if (lt.qualifiesAsBaseTableOrView()) {
//        lt.asBaseTableOrView().setTableName(database + "." + lt.asBaseTableOrView().getTableName)
//      } else {
//        val view = lt.asR2rmlView()
//        var query = view.getSqlQuery
//        query = makeQualifiedTableNames(database, query)
//        view.setSqlQuery(query)
//      }
//    })
//    model
//  }
//
//  private def makeQualifiedTableNames(qualifier: String, query: String): String = {
//    val statement = CCJSqlParserUtil.parse(query)
//    val selectStatement = statement.asInstanceOf[Select]
//    val tablesNamesFinder = new TablesNamesFinder {
//      override def visit(tableName: Table): Unit = {
//        tableName.setSchemaName(qualifier)
//      }
//    }
//    selectStatement.accept(tablesNamesFinder)
//    statement.toString
//  }
//
//  val escapeChars = Seq('"', '`')
//  /**
//   * Unescapes all SQL identifiers, i.e. the table and column names.
//   *
//   * @param model the R2RML mappings
//   * @return the modified R2RML mappings
//   */
//  def unescapeIdentifiers(model: Model): Model = {
//    escapeChars.foreach(c => replaceEscapeChars(model, s"$c", ""))
//    model
//  }
//
//  /**
//   * Replaces the escape chars of all SQL identifiers, i.e. the table and column names.
//   *
//   * @param model the R2RML mappings
//   * @param oldEscapeChar the old escape char
//   * @param newEscapeChar the new escape char
//   * @return the modified R2RML mappings
//   */
//  def replaceEscapeChars(model: Model, oldEscapeChar: String, newEscapeChar: String): Model = {
//    streamTriplesMaps(model).foreach(tm => {
//      val lt = tm.getOrSetLogicalTable()
//
//      if (lt.qualifiesAsBaseTableOrView()) {// tables
//        val tn = lt.asBaseTableOrView().getTableName
//        lt.asBaseTableOrView().setTableName(replaceIdentifier(tn, oldEscapeChar, newEscapeChar))
//      } else { // views
//        val view = lt.asR2rmlView()
//        val query = view.getSqlQuery
//        view.setSqlQuery(replaceQueryIdentifiers(query, oldEscapeChar, newEscapeChar))
//      }
//
//      // column names
//      // s
//      val sm = tm.getSubjectMap
//      if (sm != null) {
//        val col = sm.getColumn
//        if(col != null) {
//          sm.setColumn(replaceIdentifier(col, oldEscapeChar, newEscapeChar))
//        }
//      }
//
//      tm.getPredicateObjectMaps.forEach(pm => {
//        // p
//        val pms = pm.getPredicateMaps
//        if (pms != null) {
//          pms.forEach(pm => {
//            val col = pm.getColumn
//            if (col != null) {
//              pm.setColumn(replaceIdentifier(col, oldEscapeChar, newEscapeChar))
//            }
//          })
//        }
//
//        // o
//        val oms = pm.getObjectMaps
//        if (oms != null) {
//          oms.forEach(om => {
//            if (om.qualifiesAsTermMap()) {
//              val tm = om.asTermMap()
//              val col = tm.getColumn
//              if (col != null) {
//                tm.setColumn(replaceIdentifier(col, oldEscapeChar, newEscapeChar))
//              }
//            }
//          })
//        }
//      })
//    })
//
//    model
//  }
//
//  private def replaceIdentifier(identifier: String, oldEscapeChar: String, newEscapeChar: String): String = {
//    identifier.replace(oldEscapeChar, newEscapeChar)
//  }
//
//  private def replaceQueryIdentifiers(query: String, oldEscapeChar: String, newEscapeChar: String): String = {
//    val statement = CCJSqlParserUtil.parse(query)
//    val selectStatement = statement.asInstanceOf[Select]
//    val tablesNamesFinder = new TablesNamesFinder {
//      override def visit(tableName: Table): Unit = {
//        tableName.setName(tableName.getName.replace(oldEscapeChar, newEscapeChar))
//      }
//    }
//    selectStatement.accept(tablesNamesFinder)
//    statement.toString
//  }
}
