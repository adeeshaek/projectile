package com.kyleu.projectile.models.export

import com.kyleu.projectile.models.database.schema.{Column, ForeignKey}
import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.typ.FieldType
import com.kyleu.projectile.models.output.ExportHelper
import com.kyleu.projectile.models.feature.ModelFeature
import com.kyleu.projectile.models.project.member.ModelMember
import com.kyleu.projectile.models.input.InputType
import com.kyleu.projectile.util.JsonSerializers._

object ExportModel {
  implicit val jsonEncoder: Encoder[ExportModel] = deriveEncoder
  implicit val jsonDecoder: Decoder[ExportModel] = deriveDecoder
}

case class ExportModel(
    inputType: InputType.Model,
    key: String,
    pkg: List[String] = Nil,
    propertyName: String,
    className: String,
    title: String,
    description: Option[String],
    plural: String,
    arguments: List[ExportField],
    fields: List[ExportField],
    pkColumns: List[Column] = Nil,
    foreignKeys: List[ForeignKey] = Nil,
    references: List[ExportModelReference] = Nil,
    features: Set[ModelFeature] = Set.empty,
    extendsClass: Option[String] = None,
    icon: Option[String] = None,
    readOnly: Boolean = false,
    source: Option[String] = None
) {
  def apply(m: ModelMember) = copy(
    pkg = m.pkg.toList,
    propertyName = m.getOverride("propertyName", propertyName),
    className = m.getOverride("className", className),
    title = m.getOverride("title", title),
    plural = m.getOverride("plural", plural),
    features = m.features,
    fields = fields.filterNot(f => m.ignored.contains(f.key)).map(f => f.copy(
      propertyName = m.getOverride(s"${f.key}.propertyName", f.propertyName),
      title = m.getOverride(s"${f.key}.title", f.title),
      t = m.getOverride(s"${f.key}.type", "") match {
        case "" => f.t
        case x => FieldType.withValue(x)
      },
      inSearch = m.getOverride(s"${f.key}.search", f.inSearch.toString).toBoolean
    )),
    foreignKeys = foreignKeys.filterNot(fk => m.ignored.contains("fk." + fk.name)).map { fk =>
      fk.copy(propertyName = m.getOverride(s"fk.${fk.name}.propertyName", fk.propertyName))
    },
    references = references.filterNot(r => m.ignored.contains("reference." + r.name)).map { r =>
      r.copy(propertyName = m.getOverride(s"reference.${r.name}.propertyName", r.propertyName))
    }
  )

  val fullClassName = (pkg :+ className).mkString(".")
  def fullClassPath(config: ExportConfiguration) = (modelPackage(config) :+ className).mkString(".")
  val firstPackage = pkg.headOption.getOrElse("")
  val propertyPlural = ExportHelper.toIdentifier(plural)

  val pkFields = pkColumns.flatMap(c => getFieldOpt(c.name))
  def pkType(config: ExportConfiguration) = pkFields match {
    case Nil => "???"
    case h :: Nil => h.scalaType(config)
    case cols => "(" + cols.map(_.scalaType(config)).mkString(", ") + ")"
  }

  val indexedFields = fields.filter(_.indexed).filterNot(_.t == FieldType.TagsType)
  val searchFields = fields.filter(_.inSearch)
  val summaryFields = fields.filter(_.inSummary).filterNot(x => pkFields.exists(_.key == x.key))

  def searchCols = (foreignKeys.flatMap(_.references match {
    case ref :: Nil => Some(ref.source)
    case _ => None
  }) ++ searchFields.map(_.key)).distinct.sorted

  def modelPackage(config: ExportConfiguration) = {
    val prelude = if (inputType.isThrift) { Nil } else { config.applicationPackage }
    prelude ++ (pkg.lastOption match {
      case Some("models") => pkg
      case Some("fragment") => pkg
      case Some("input") => pkg
      case Some("mutation") => pkg
      case Some("query") => pkg
      case _ if inputType == InputType.Model.TypeScriptModel => pkg
      case _ => "models" +: pkg
    })
  }

  def queriesPackage(config: ExportConfiguration) = config.applicationPackage ++ List("models", "queries") ++ pkg
  def graphqlPackage(config: ExportConfiguration) = config.applicationPackage ++ List("models", "graphql") ++ pkg
  def slickPackage(config: ExportConfiguration) = config.applicationPackage ++ List("models", "table") ++ pkg
  def doobiePackage(config: ExportConfiguration) = config.applicationPackage ++ List("models", "doobie") ++ pkg
  def servicePackage(config: ExportConfiguration) = config.applicationPackage ++ List("services") ++ pkg
  def controllerPackage(config: ExportConfiguration) = {
    config.applicationPackage ++ List("controllers", "admin") ++ (if (pkg.isEmpty) { List("system") } else { pkg })
  }
  def routesPackage(config: ExportConfiguration) = controllerPackage(config) :+ "routes"
  def viewPackage(config: ExportConfiguration) = config.applicationPackage ++ Seq("views", "admin") ++ pkg
  def viewHtmlPackage(config: ExportConfiguration) = config.applicationPackage ++ Seq("views", "html", "admin") ++ pkg

  def injectedService(config: ExportConfiguration) = s"injector.getInstance(classOf[${(servicePackage(config) :+ className).mkString(".")}Service])"

  def getField(k: String) = getFieldOpt(k).getOrElse {
    throw new IllegalStateException(s"No field for model [$className] with name [$k]. Available fields: [${fields.map(_.propertyName).mkString(", ")}]")
  }
  def getFieldOpt(k: String) = fields.find(f => f.key == k || f.propertyName == k)
  def perm(act: String) = s"""("${pkg.headOption.getOrElse("system")}", "$className", "$act")"""
}
