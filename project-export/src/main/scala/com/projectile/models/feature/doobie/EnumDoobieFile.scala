package com.projectile.models.feature.doobie

import com.projectile.models.export.ExportEnum
import com.projectile.models.export.config.ExportConfiguration
import com.projectile.models.output.OutputPath
import com.projectile.models.feature.EnumFeature
import com.projectile.models.output.file.ScalaFile

object EnumDoobieFile {
  def export(config: ExportConfiguration, enum: ExportEnum) = {
    val path = if (enum.features(EnumFeature.Shared)) { OutputPath.SharedSource } else { OutputPath.ServerSource }
    val file = ScalaFile(path = path, dir = config.applicationPackage ++ enum.doobiePackage, key = enum.className + "Doobie")

    file.addImport(config.applicationPackage ++ enum.modelPackage, enum.className)
    config.addCommonImport(file, "DoobieQueryService", "Imports", "_")

    file.add(s"object ${enum.className}Doobie {", 1)
    val cn = enum.className
    file.add(s"""implicit val ${enum.propertyName}Meta: Meta[$cn] = pgEnumStringOpt("$cn", $cn.withValueOpt, _.value)""")
    file.add("}", -1)

    file
  }
}
