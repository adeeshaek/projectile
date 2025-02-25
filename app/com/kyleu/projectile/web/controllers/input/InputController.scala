package com.kyleu.projectile.web.controllers.input

import com.kyleu.projectile.models.database.input.{PostgresConnection, PostgresInput}
import com.kyleu.projectile.models.graphql.input.GraphQLInput
import com.kyleu.projectile.models.input.{InputSummary, InputTemplate}
import com.kyleu.projectile.models.thrift.input.{ThriftInput, ThriftOptions}
import com.kyleu.projectile.models.typescript.input.{TypeScriptInput, TypeScriptOptions}
import com.kyleu.projectile.services.input.PostgresInputService
import com.kyleu.projectile.web.controllers.ProjectileController
import com.kyleu.projectile.web.util.ControllerUtils

import scala.concurrent.Future

@javax.inject.Singleton
class InputController @javax.inject.Inject() () extends ProjectileController {
  def detail(key: String) = Action.async { implicit request =>
    val view = projectile.getInput(key) match {
      case i: PostgresInput => com.kyleu.projectile.web.views.html.input.postgresInput(projectile, i)
      case t: ThriftInput => com.kyleu.projectile.web.views.html.input.thriftInput(projectile, t)
      case g: GraphQLInput => com.kyleu.projectile.web.views.html.input.graphQLInput(projectile, g)
      case t: TypeScriptInput => com.kyleu.projectile.web.views.html.input.typeScriptInput(projectile, t)
      case x => throw new IllegalStateException(s"Cannot render view for [$x]")
    }
    Future.successful(Ok(view))
  }

  def refresh(key: String) = Action.async { _ =>
    val startMs = System.currentTimeMillis
    projectile.refreshInput(key)
    val msg = s"Refreshed input [$key] in [${System.currentTimeMillis - startMs}ms]"
    Future.successful(Redirect(com.kyleu.projectile.web.controllers.input.routes.InputController.detail(key)).flashing("success" -> msg.take(512)))
  }

  def refreshAll = Action.async { _ =>
    val startMs = System.currentTimeMillis
    val results = projectile.listInputs().map(i => projectile.refreshInput(i.key))
    val msg = s"Refreshed [${results.size}] inputs in [${System.currentTimeMillis - startMs}ms]"
    Future.successful(Redirect(com.kyleu.projectile.web.controllers.routes.HomeController.index()).flashing("success" -> msg.take(512)))
  }

  def formNew = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.formNew(projectile)))
  }

  def form(key: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.form(projectile, projectile.getInput(key))))
  }

  def save() = Action.async { implicit request =>
    val form = ControllerUtils.getForm(request.body)
    val summary = InputSummary(template = InputTemplate.withValue(form("template")), key = form("key"), description = form("description"))
    lazy val files = form.getOrElse("files", "").split("\n").map(_.trim).filter(_.nonEmpty)
    val input = projectile.addInput(summary)
    val msg = "success" -> s"Saved input [${input.key}]"
    summary.template match {
      case InputTemplate.Postgres => projectile.setPostgresOptions(summary.key, PostgresConnection(
        host = form.getOrElse("host", "localhost"),
        port = form.getOrElse("port", "5432").toInt,
        username = form.getOrElse("username", "postgres"),
        password = Some(form.getOrElse("password", "password")).filter(_ != "-unchanged-").getOrElse(PostgresInputService.loadConnection(projectile.rootCfg, summary.key).password),
        db = form.getOrElse("db", "")
      ))
      case InputTemplate.GraphQL => // projectile.setGraphQLOptions(summary.key, GraphQLOptions(???))
      case InputTemplate.Thrift => projectile.setThriftOptions(summary.key, ThriftOptions(files = files))
      case InputTemplate.TypeScript => projectile.setTypeScriptOptions(summary.key, TypeScriptOptions(files = files))
    }
    Future.successful(Redirect(com.kyleu.projectile.web.controllers.input.routes.InputController.detail(input.key)).flashing(msg))
  }

  def remove(key: String) = Action.async { _ =>
    val removed = projectile.removeInput(key)
    val msg = "success" -> s"Removed input [$key]: $removed"
    Future.successful(Redirect(com.kyleu.projectile.web.controllers.routes.HomeController.index()).flashing(msg))
  }

  def enumDetail(key: String, enum: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.detailEnum(projectile, projectile.getInput(key).enum(enum))))
  }

  def modelDetail(key: String, model: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.detailModel(projectile, projectile.getInput(key).model(model))))
  }

  def unionDetail(key: String, model: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.detailUnion(projectile, projectile.getInput(key).union(model))))
  }

  def serviceDetail(key: String, service: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.input.detailService(projectile, projectile.getInput(key).service(service))))
  }
}
