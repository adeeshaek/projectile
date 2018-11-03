package models.export.config

import models.export.{ExportEnum, ExportModel}
import models.output.OutputPackage
import models.project.Project
import util.JsonSerializers._

object ExportConfiguration {
  implicit val jsonEncoder: Encoder[ExportConfiguration] = deriveEncoder
  implicit val jsonDecoder: Decoder[ExportConfiguration] = deriveDecoder
}

case class ExportConfiguration(
    project: Project,
    enums: Seq[ExportEnum],
    models: Seq[ExportModel]
) {
  val applicationPackage = project.getPackage(OutputPackage.Application)

  val systemPackage = project.getPackage(OutputPackage.System)

  val resultsPackage = systemPackage ++ project.getPackage(OutputPackage.Results)
  val tagsPackage = systemPackage ++ project.getPackage(OutputPackage.Tags)
  val utilitiesPackage = systemPackage ++ project.getPackage(OutputPackage.Utils)

  def getEnum(k: String) = getEnumOpt(k).getOrElse(throw new IllegalStateException(s"No enum available with name [$k]."))
  def getEnumOpt(k: String) = enums.find(e => e.key == k || e.propertyName == k || e.className == k)

  def getModel(k: String) = getModelOpt(k).getOrElse(throw new IllegalStateException(s"No model available with name [$k]."))
  def getModelOpt(k: String) = models.find(m => m.key == k || m.propertyName == k || m.className == k)

  lazy val packages = {
    val (rootEnums, packageEnums) = enums.partition(_.pkg.isEmpty)
    val enumPackages = packageEnums.groupBy(_.pkg.head).toSeq.sortBy(_._1)

    val (rootModels, packageModels) = models.partition(_.pkg.isEmpty)
    val modelPackages = packageModels.groupBy(_.pkg.head).toSeq.sortBy(_._1)

    val packages = (enumPackages.map(_._1) ++ modelPackages.map(_._1)).distinct

    packages.map { p =>
      val ms = modelPackages.filter(_._1 == p).flatMap(_._2)
      val es = enumPackages.filter(_._1 == p).flatMap(_._2)

      val solo = ms.size == 1 && es.isEmpty
      (p, ms, es, solo)
    }
  }

}
