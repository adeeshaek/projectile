package com.kyleu.projectile.controllers.admin.graphql

import com.kyleu.projectile.controllers.AuthController
import com.kyleu.projectile.controllers.admin.graphql.routes.GraphQLController
import com.kyleu.projectile.graphql.GraphQLService
import com.kyleu.projectile.models.user.SystemUser
import io.circe.Json
import com.kyleu.projectile.models.auth.UserCredentials
import com.kyleu.projectile.models.menu.SystemMenu
import com.kyleu.projectile.models.module.ApplicationFeature.Graphql.value
import com.kyleu.projectile.models.module.{Application, ApplicationFeature}
import com.kyleu.projectile.util.EncryptionUtils
import sangria.execution.{ErrorWithResolver, QueryAnalysisError}
import sangria.marshalling.circe._
import sangria.parser.SyntaxError
import com.kyleu.projectile.util.tracing.TraceData
import com.kyleu.projectile.models.web.ControllerUtils.{jsonBody, jsonObject}
import com.kyleu.projectile.models.web.InternalIcons
import com.kyleu.projectile.services.auth.PermissionService

import scala.concurrent.{ExecutionContext, Future}

@javax.inject.Singleton
class GraphQLController @javax.inject.Inject() (
    override val app: Application, graphQLService: GraphQLService
)(implicit ec: ExecutionContext) extends AuthController("graphql") {
  ApplicationFeature.enable(ApplicationFeature.Graphql)
  PermissionService.registerModel("tools", "GraphQL", "GraphQL", Some(InternalIcons.graphql), "post", "ide", "visualize")
  val description = "A full GraphQL IDE and schema visualizer"
  SystemMenu.addRootMenu(value, "GraphQL", Some(description), GraphQLController.iframe(), InternalIcons.graphql, ("", "", ""))

  private[this] val secretKey = "GraphTastesBad"

  def iframe() = withSession("iframe", ("tools", "GraphQL", "ide")) { implicit request => implicit td =>
    Future.successful(Ok(com.kyleu.projectile.views.html.graphql.iframe(app.cfg(u = Some(request.identity), "system", "graphql"))))
  }

  def graphql(query: Option[String], variables: Option[String]) = {
    withSession("ide", ("tools", "GraphQL", "ide")) { implicit request => implicit td =>
      Future.successful(Ok(com.kyleu.projectile.views.html.graphql.graphiql(app.cfg(u = Some(request.identity), "system", "graphql"))))
    }
  }

  def graphqlBody = withoutSession("post") { implicit request => implicit td =>
    val allowed = request.identity match {
      case Some(u) if PermissionService.check(u.role, "tools", "GraphQL", "ide")._1 => true // All cool, permissions passed
      case Some(_) => false
      case None if request.headers.get("admin-graphql-auth").exists(x => EncryptionUtils.decrypt(x) == secretKey) => true // All Cool, config backdoor
      case None =>
        val enc = EncryptionUtils.encrypt(secretKey)
        log.warn(s"Invalid graphql authentication. To access the server without logging in, add the header [admin-graphql-auth] with value [$enc]")
        false
    }

    if (allowed) {
      val body = jsonObject(jsonBody(request.body)).toMap
      val query = body("query").as[String].getOrElse("{}")
      val variables = body.get("variables").map(x => graphQLService.parseVariables(x.toString))
      val operation = body.get("operationName").flatMap(_.asString)

      val creds = UserCredentials.fromInsecureRequest(request).getOrElse(UserCredentials(user = SystemUser.api, remoteAddress = request.remoteAddress))
      executeQuery(query, variables, operation, creds, app.config.debug)
    } else {
      failRequest(request)
    }
  }

  private[this] def executeQuery(
    query: String, variables: Option[Json], operation: Option[String], creds: UserCredentials, debug: Boolean
  )(implicit data: TraceData) = {
    try {
      val f = graphQLService.executeQuery(query, variables, operation, creds, debug)
      f.map(x => Ok(x)).recover {
        case error: QueryAnalysisError => BadRequest(error.resolveError)
        case error: ErrorWithResolver => InternalServerError(error.resolveError)
      }
    } catch {
      case error: SyntaxError =>
        val json = Json.obj(
          "syntaxError" -> Json.fromString(error.getMessage),
          "locations" -> Json.arr(Json.obj(
            "line" -> Json.fromInt(error.originalError.position.line),
            "column" -> Json.fromInt(error.originalError.position.column)
          ))
        )
        Future.successful(BadRequest(json))
    }
  }
}
