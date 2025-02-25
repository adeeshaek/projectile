package com.kyleu.projectile.models.web

import com.kyleu.projectile.controllers.Assets
import com.kyleu.projectile.models.result.data.DataField
import com.kyleu.projectile.util.JsonSerializers.Json
import com.kyleu.projectile.util.{JsonSerializers, NullUtils}
import play.api.data.FormError
import play.api.mvc.AnyContent
import play.twirl.api.Html

object ControllerUtils {
  def getForm(body: AnyContent, prefix: Option[String] = None) = body.asFormUrlEncoded match {
    case Some(f) =>
      val fullMap = f.mapValues(_.mkString(","))
      prefix.map(p => fullMap.filterKeys(_.startsWith(p)).map(x => x._1.stripPrefix(p) -> x._2)).getOrElse(fullMap)
    case None => throw new IllegalStateException("Missing form post")
  }

  def errorsToString(errors: Seq[FormError]) = errors.map(e => e.key + ": " + e.message).mkString(", ")

  def jsonBody(body: AnyContent) = body.asJson.map { json =>
    JsonSerializers.parseJson(play.api.libs.json.Json.stringify(json)) match {
      case Right(x) => x
      case Left(x) => throw x
    }
  }.getOrElse(throw new IllegalStateException("Http post with json body required"))

  def jsonFormOrBody(body: AnyContent, key: String) = {
    val content = body.asFormUrlEncoded.map(_(key).headOption.getOrElse(throw new IllegalStateException(s"Missing form field [$key]")))
    content.map(JsonSerializers.parseJson).map {
      case Right(x) => x
      case Left(x) => throw x
    }.getOrElse(jsonBody(body))
  }

  def jsonObject(json: Json) = json.asObject.getOrElse(throw new IllegalStateException("Json is not an object"))

  def jsonArguments(body: AnyContent, arguments: String*) = {
    val json = jsonObject(jsonFormOrBody(body, "arguments"))
    arguments.map(arg => json(arg) match {
      case Some(argJson) => arg -> argJson
      case None => throw new IllegalStateException(s"Missing argument [$arg] in body")
    }).toMap
  }

  def modelForm(rawForm: Map[String, Seq[String]]) = {
    val form = rawForm.mapValues(_.headOption.getOrElse(throw new IllegalStateException("Empty form field")))
    val fields = form.toSeq.filter(x => x._1.endsWith("-include") && x._2 == "true").map(_._1.stripSuffix("-include"))
    def valFor(f: String) = form.get(f) match {
      case Some(x) if x == NullUtils.str => None
      case Some(x) => Some(x)
      case None => form.get(f + "-date") match {
        case Some(d) => Some(s"$d${form.get(f + "-time").map(" " + _).getOrElse("")}")
        case None => Some(form.getOrElse(f + "-time", throw new IllegalStateException(s"Cannot find value for included field [$f]")))
      }
    }
    fields.map(f => DataField(f, valFor(f).map(_.trim)))
  }

  lazy val commonScripts = Seq(Assets.path("vendor/vendors.min.js"), Assets.path("vendor/plugins.min.js"))
  lazy val commonStylesheets = Seq(
    "/assets/lib/material-design-icons/material-icons.css",
    Assets.path(s"vendor/theme/default/materialize.css"),
    Assets.path("vendor/vendors.min.css"),
    Assets.path(s"vendor/theme/default/style.css"),
    Assets.path(s"stylesheets/components.css")
  )

  lazy val dataTableScripts = Seq(Assets.path("vendor/dataTables/dataTables.min.js"))
  lazy val dataTableStylesheets = Seq(Assets.path("vendor/dataTables/dataTables.min.css"))

  lazy val nestableScripts = Seq(Assets.path("vendor/nestable/nestable.js"))
  lazy val nestableStylesheets = Seq(Assets.path("vendor/nestable/nestable.css"))
  lazy val nestableIncludeSnippet = snippet(nestableScripts, nestableStylesheets)
  def nestableData(json: Json) = json.asArray.get.map(_.asObject.get.apply("id").get.asString.get)

  private[this] def snippet(scripts: Seq[String], stylesheets: Seq[String]) = {
    Html((scripts.map(s => s"""<script src="$s"></script>""") ++ stylesheets.map(s => s"""<link rel="stylesheet" media="screen" href="$s" />""")).mkString("\n"))
  }
}
