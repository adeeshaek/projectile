package com.kyleu.projectile.models.feature.core

import com.kyleu.projectile.models.export.ExportModel
import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.typ.{FieldType, FieldTypeAsScala, FieldTypeImports, FieldTypeRestrictions}
import com.kyleu.projectile.models.feature.ModelFeature
import com.kyleu.projectile.models.output.file.ScalaFile

object ModelHelper {
  def addFields(config: ExportConfiguration, model: ExportModel, file: ScalaFile) = model.fields.foreach { field =>
    field.addImport(config, file, model.modelPackage(config))

    if (FieldTypeRestrictions.isDate(field.t)) {
      config.addCommonImport(file, "DateUtils")
    }

    val scalaJsPrefix = if (model.features(ModelFeature.ScalaJS)) { "@JSExport " } else { "" }

    val colScala = field.t match {
      case FieldType.TagsType =>
        config.addCommonImport(file, "Tag")
        field.scalaType(config)
      case _ => field.scalaType(config)
    }
    val propType = if (field.required) { colScala } else { "Option[" + colScala + "]" }
    val propDecl = s"$scalaJsPrefix${field.propertyName}: $propType"
    val comma = if (model.fields.lastOption.contains(field)) { "" } else { "," }
    field.description.foreach(d => file.add("/** " + d + " */"))
    file.add(propDecl + comma)
  }

  def addEmpty(config: ExportConfiguration, model: ExportModel, file: ScalaFile) = {
    val fieldStrings = model.fields.map { field =>
      FieldTypeImports.imports(config, field.t).foreach {
        case x if x == Seq("java", "time") => config.addCommonImport(file, "DateUtils")
        case _ => //noop
      }

      val colScala = field.t match {
        case _ => field.scalaType(config)
      }
      val propType = if (field.required) { colScala } else { "Option[" + colScala + "]" }
      val propDefault = if (field.required) {
        " = " + field.defaultString(config)
      } else {
        " = None"
      }
      s"${field.propertyName}: $propType$propDefault"
    }
    file.add(s"def empty(", 1)
    fieldStrings.foreach {
      case x if fieldStrings.lastOption.contains(x) => file.add(x)
      case x => file.add(x + ",")
    }
    file.add(s") = {", -1)
    file.indent()
    file.add(s"${model.className}(${model.fields.map(_.propertyName).mkString(", ")})")
    file.add("}", -1)
  }

  def addJson(config: ExportConfiguration, file: ScalaFile, model: ExportModel) = if (model.features(ModelFeature.Json)) {
    file.add(s"implicit val jsonEncoder: Encoder[${model.className}] = (r: ${model.className}) => io.circe.Json.obj(", 1)
    model.fields.foreach { f =>
      val comma = if (model.fields.lastOption.contains(f)) { "" } else { "," }
      file.add(s"""("${f.propertyName}", r.${f.propertyName}.asJson)$comma""")
    }
    file.add(")", -1)
    file.add()

    file.add(s"implicit val jsonDecoder: Decoder[${model.className}] = (c: io.circe.HCursor) => for {", 1)
    model.fields.foreach { f =>
      val ts = FieldTypeAsScala.asScala(config = config, t = f.t)
      val typ = if (f.required) { ts } else { "Option[" + ts + "]" }
      file.add(s"""${f.propertyName} <- c.downField("${f.propertyName}").as[$typ]""")
    }
    val props = model.fields.map(_.propertyName).mkString(", ")
    file.add(s"} yield ${model.className}($props)", -1)
    file.add()
  }
}
