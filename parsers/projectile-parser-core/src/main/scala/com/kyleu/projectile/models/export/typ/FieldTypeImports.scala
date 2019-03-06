package com.kyleu.projectile.models.export.typ

import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.typ.FieldType._
import com.kyleu.projectile.models.output.CommonImportHelper

object FieldTypeImports {
  def imports(config: ExportConfiguration, t: FieldType): Seq[Seq[String]] = t match {
    case UuidType => Seq(Seq("java", "util") :+ FieldTypeAsScala.asScala(config, t))
    case DateType | TimeType | TimestampType | TimestampZonedType => Seq(Seq("java", "time") :+ FieldTypeAsScala.asScala(config, t))
    case JsonType => Seq(Seq("io", "circe") :+ FieldTypeAsScala.asScala(config, t))
    case TagsType =>
      val ret = CommonImportHelper.get(config, "Tag")
      Seq(ret._1 :+ ret._2)
    case EnumType(key) => config.getEnumOpt(key).map(_.modelPackage(config) :+ FieldTypeAsScala.asScala(config, t)).toSeq
    case StructType(key) => config.getModelOpt(key).map(_.modelPackage(config) :+ FieldTypeAsScala.asScala(config, t)).toSeq

    case ListType(typ) => imports(config, typ)
    case SetType(typ) => imports(config, typ)
    case MapType(k, v) => imports(config, k) ++ imports(config, v)
    case _ => Nil
  }
}
