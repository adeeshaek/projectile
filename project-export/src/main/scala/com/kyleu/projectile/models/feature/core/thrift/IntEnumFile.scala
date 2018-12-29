package com.kyleu.projectile.models.feature.core.thrift

import com.kyleu.projectile.models.export.ExportEnum
import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.feature.EnumFeature
import com.kyleu.projectile.models.output.{ExportHelper, OutputPath}
import com.kyleu.projectile.models.output.file.ScalaFile

object IntEnumFile {
  def export(config: ExportConfiguration, enum: ExportEnum) = {
    val path = if (enum.features(EnumFeature.Shared)) { OutputPath.SharedSource } else { OutputPath.ServerSource }
    val file = ScalaFile(path = path, dir = enum.pkg, key = enum.className)
    val thriftEnum = enum.pkg.dropRight(1) :+ enum.key

    file.addImport(Seq("enumeratum", "values"), "IntCirceEnum")
    file.addImport(Seq("enumeratum", "values"), "IntEnum")
    file.addImport(Seq("enumeratum", "values"), "IntEnumEntry")

    file.add(s"sealed abstract class ${enum.className}(override val value: Int, val name: String) extends IntEnumEntry {", 1)
    file.add(s"lazy val asThrift = ${thriftEnum.mkString(".")}.apply(value)")
    file.add("override def toString = name")
    file.add("}", -1)
    file.add()

    file.add(s"object ${enum.className} extends IntEnum[${enum.className}] with IntCirceEnum[${enum.className}] {", 1)
    addFields(enum, file)
    file.add()
    file.add("override val values = findValues")
    file.add(s"def fromThrift(t: ${thriftEnum.mkString(".")}) = ${enum.key}.withValue(t.getValue)")

    file.add("def withName(s: String) = values.find(_.name == s).getOrElse {", 1)
    file.add(s"""throw new IllegalStateException(s"No ${enum.className} with name [$$s] among candidates [$${values.map(_.name).mkString(", ")}]")""")
    file.add("}", -1)

    file.add("}", -1)

    file
  }

  private[this] def addFields(model: ExportEnum, file: ScalaFile) = model.valuesWithClassNames.foreach { v =>
    val (i, s) = v._1.indexOf(':') match {
      case -1 => 0 -> v._1
      case x => v._1.substring(0, x).toInt -> v._1.substring(x + 1)
    }
    file.add(s"""case object ${ExportHelper.toClassName(v._2)} extends ${model.className}($i, "$s")""")
  }
}
