package com.kyleu.projectile.services

import com.kyleu.projectile.models.result.data.DataField
import com.kyleu.projectile.models.result.filter.Filter
import com.kyleu.projectile.models.result.orderBy.OrderBy
import com.kyleu.projectile.util.{Logging, NullUtils}
import com.kyleu.projectile.util.tracing.{TraceData, TracingService}

import scala.concurrent.{ExecutionContext, Future}

abstract class ModelServiceHelper[T](val key: String)(implicit ec: ExecutionContext) extends Logging {
  def tracing: TracingService

  def traceF[A](name: String)(f: TraceData => Future[A])(implicit trace: TraceData) = tracing.trace(key + ".service." + name)(td => f(td))
  def traceB[A](name: String)(f: TraceData => A)(implicit trace: TraceData) = tracing.traceBlocking(key + ".service." + name)(td => f(td))

  def countAll(creds: Credentials, filters: Seq[Filter])(implicit trace: TraceData): Future[Int]
  def getAll(
    creds: Credentials, filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int] = None, offset: Option[Int] = None
  )(implicit trace: TraceData): Future[Seq[T]]

  def getAllWithCount(
    creds: Credentials, filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int] = None, offset: Option[Int] = None
  )(implicit trace: TraceData) = traceF("get.all.with.count") { td =>
    val result = getAll(creds, filters, orderBys, limit, offset)(td)
    val count = countAll(creds, filters)(td)
    count.flatMap(c => result.map(x => c -> x))
  }

  def searchCount(creds: Credentials, q: Option[String], filters: Seq[Filter])(implicit trace: TraceData): Future[Int]
  def search(
    creds: Credentials, q: Option[String], filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int], offset: Option[Int]
  )(implicit trace: TraceData): Future[Seq[T]]

  def searchWithCount(
    creds: Credentials, q: Option[String], filters: Seq[Filter], orderBys: Seq[OrderBy], limit: Option[Int] = None, offset: Option[Int] = None
  )(implicit trace: TraceData) = traceF("search.with.count") { td =>
    val result = search(creds, q, filters, orderBys, limit, offset)(td)
    val count = searchCount(creds, q, filters)(td)
    count.flatMap(c => result.map(x => c -> x))
  }

  protected def fieldVal(fields: Seq[DataField], k: String) = fields.find(_.k == k).flatMap(_.v).getOrElse(NullUtils.str)
}
