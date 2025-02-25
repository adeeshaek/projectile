package com.kyleu.projectile.models.feature.controller.db

import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.{ExportEnum, ExportModel, ExportModelReference}
import com.kyleu.projectile.models.feature.{EnumFeature, ModelFeature}
import com.kyleu.projectile.models.output.file.RoutesFile

object RoutesFiles {
  private[this] val limitArgs = "limit: Option[Int] ?= None, offset: Option[Int] ?= None, t: Option[String] ?= None"
  private[this] val listArgs = "q: Option[String] ?= None, orderBy: Option[String] ?= None, orderAsc: Boolean ?= true, " + limitArgs
  private[this] val autocompleteArgs = "q: Option[String] ?= None, orderBy: Option[String] ?= None, orderAsc: Boolean ?= true, limit: Option[Int] ?= None"
  private[this] val relationArgs = s"orderBy: Option[String] ?= None, orderAsc: Boolean ?= true, $limitArgs, embedded: Boolean ?= false"

  def export(config: ExportConfiguration) = {
    val filtered = config.models.filter(_.features(ModelFeature.Controller)).filter(_.inputType.isDatabase)
    val packages = filtered.flatMap(_.pkg.headOption).distinct

    val routesContent = packages.map { p =>
      val ms = config.models.filter(_.features(ModelFeature.Controller)).filter(_.pkg.headOption.contains(p))
      val es = config.enums.filter(_.features(EnumFeature.Controller)).filter(_.pkg.headOption.contains(p))
      val solo = es.isEmpty && ms.size == 1

      if (solo) {
        p -> routesContentFor(config, ms.headOption.getOrElse(throw new IllegalStateException()), solo = true)
      } else {
        p -> (ms.flatMap(m => routesContentFor(config, m)) ++ es.flatMap(e => enumRoutesContentFor(config, e)))
      }
    }

    routesContent.map { p =>
      val f = RoutesFile(p._1)
      p._2.foreach(l => f.add(l))
      f
    }
  }

  def routesContentFor(config: ExportConfiguration, model: ExportModel, solo: Boolean = false) = {
    val controllerClass = (model.controllerPackage(config) :+ (model.className + "Controller")).mkString(".")

    val prefix = if (solo) { "" } else { s"/${model.propertyName}" }
    val root = if (solo) { "/" } else { s"/${model.propertyName}" }

    val comment = s"# ${model.title} Routes"
    val listWs = (0 until (56 - root.length)).map(_ => " ").mkString
    val list = s"GET         $root $listWs $controllerClass.list($listArgs)"
    val autocomplete = s"GET         $prefix/autocomplete ${listWs.drop(13)} $controllerClass.autocomplete($autocompleteArgs)"
    val createForm = s"GET         $prefix/form ${listWs.drop(5)} $controllerClass.createForm"
    val createAct = s"POST        $root $listWs $controllerClass.create"
    val fks = model.foreignKeys.flatMap { fk =>
      fk.references match {
        case h :: Nil =>
          val col = model.fields.find(_.key == h.source).getOrElse(throw new IllegalStateException(s"Missing column [${h.source}]"))
          val urlArgs = s"by${col.className}/:${col.propertyName}"
          val detailUrl = prefix + "/" + urlArgs
          val detailWs = (0 until (56 - detailUrl.length)).map(_ => " ").mkString
          val t = col.scalaTypeFull(config).mkString(".")
          Some(s"GET         $detailUrl $detailWs $controllerClass.by${col.className}(${col.propertyName}: $t, $relationArgs)")
        case _ => None
      }
    }
    val detail = model.pkFields match {
      case Nil => Nil
      case pkFields =>
        val args = pkFields.map { x =>
          s"${x.propertyName}: ${x.scalaTypeFull(config).mkString(".")}"
        }.mkString(", ")
        val urlArgs = pkFields.map(f => ":" + f.propertyName).mkString("/")

        val detailUrl = prefix + "/" + urlArgs
        val detailWs = (0 until (56 - detailUrl.length)).map(_ => " ").mkString

        val view = s"GET         $detailUrl $detailWs $controllerClass.view($args, t: Option[String] ?= None)"
        val counts = if (ExportModelReference.validReferences(config, model).isEmpty) {
          Nil
        } else {
          Seq(s"GET         $detailUrl/counts ${detailWs.drop(7)} $controllerClass.relationCounts($args)")
        }
        val extras = Seq(
          s"GET         $detailUrl/form ${detailWs.drop(5)} $controllerClass.editForm($args)",
          s"POST        $detailUrl $detailWs $controllerClass.edit($args)",
          s"GET         $detailUrl/remove ${detailWs.drop(7)} $controllerClass.remove($args)"
        )
        view +: (counts ++ extras)
    }
    "" +: comment +: list +: autocomplete +: createForm +: createAct +: (fks ++ detail)
  }

  private[this] def enumRoutesContentFor(config: ExportConfiguration, e: ExportEnum) = {
    val controllerClass = (e.controllerPackage(config) :+ (e.className + "Controller")).mkString(".")
    val detailWs = (0 until (55 - e.propertyName.length)).map(_ => " ").mkString
    Seq(s"GET         /${e.propertyName} $detailWs $controllerClass.list()", "")
  }
}
