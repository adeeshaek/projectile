package com.kyleu.projectile.services.user

import com.google.inject.name.Named
import com.kyleu.projectile.models.queries.auth.SystemUserQueries
import com.kyleu.projectile.models.user.SystemUser
import com.kyleu.projectile.services.cache.UserCache
import com.kyleu.projectile.services.database.JdbcDatabase
import com.kyleu.projectile.util.Logging
import com.kyleu.projectile.util.tracing.{OpenTracingService, TraceData}
import com.mohiva.play.silhouette.api.LoginInfo
import com.mohiva.play.silhouette.api.services.IdentityService

import scala.concurrent.Future
import scala.util.control.NonFatal

@javax.inject.Singleton
class SystemUserSearchService @javax.inject.Inject() (
    @Named("system") db: JdbcDatabase,
    tracingService: OpenTracingService
) extends IdentityService[SystemUser] with Logging {
  override def retrieve(loginInfo: LoginInfo) = getByLoginInfo(loginInfo)(TraceData.noop)

  def getByLoginInfo(loginInfo: LoginInfo)(implicit trace: TraceData) = {
    tracingService.trace("user.get.by.login.info") { td =>
      UserCache.getUserByLoginInfo(loginInfo) match {
        case Some(u) => Future.successful(Some(u))
        case None => try {
          val u = db.query(SystemUserQueries.FindUserByProfile(loginInfo))(td)
          u.foreach(UserCache.cacheUser)
          Future.successful(u)
        } catch {
          case NonFatal(x) =>
            log.warn(s"Error retrieving system user by login info [$loginInfo]", x)
            Future.successful(None)
        }
      }
    }
  }
}
