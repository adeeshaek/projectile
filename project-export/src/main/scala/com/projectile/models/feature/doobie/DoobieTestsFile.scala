package com.projectile.models.feature.doobie

import com.projectile.models.export.ExportModel
import com.projectile.models.export.config.ExportConfiguration
import com.projectile.models.output.OutputPath
import com.projectile.models.output.file.ScalaFile

object DoobieTestsFile {
  def export(config: ExportConfiguration, model: ExportModel) = {
    val file = ScalaFile(path = OutputPath.ServerTest, dir = config.applicationPackage ++ model.doobiePackage, key = model.className + "DoobieTests")

    file.addImport(Seq("org", "scalatest"), "_")
    file.addImport(config.applicationPackage ++ model.modelPackage, model.className)
    config.addCommonImport(file, "DoobieQueryService", "Imports", "_")

    model.fields.foreach(_.enumOpt(config).foreach { e =>
      file.addImport(config.applicationPackage ++ e.doobiePackage :+ s"${e.className}Doobie", s"${e.propertyName}Meta")
    })

    file.add(s"class ${model.className}DoobieTests extends FlatSpec with Matchers {", 1)
    config.addCommonImport(file, "DoobieTestHelper", "yolo", "_")

    file.add()
    file.add(s""""Doobie queries for [${model.className}]" should "typecheck" in {""", 1)
    file.add(s"${model.className}Doobie.countFragment.query[Long].check.unsafeRunSync")
    file.add(s"${model.className}Doobie.selectFragment.query[${model.className}].check.unsafeRunSync")
    val search = s"""(${model.className}Doobie.selectFragment ++ whereAnd(${model.className}Doobie.searchFragment("...")))"""
    file.add(s"$search.query[${model.className}].check.unsafeRunSync")
    file.add("}", -1)

    file.add("}", -1)

    file
  }
}
