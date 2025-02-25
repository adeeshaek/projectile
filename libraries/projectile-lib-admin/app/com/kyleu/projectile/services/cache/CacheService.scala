package com.kyleu.projectile.services.cache

import net.sf.ehcache.{CacheManager, Element, ObjectExistsException}
import org.apache.commons.lang3.reflect.TypeUtils

import scala.reflect.ClassTag

object CacheService {
  private[this] val projectId = "app"

  private[this] var initialized = false

  private[this] var manager: Option[CacheManager] = None
  def mgr = manager.getOrElse {
    synchronized {
      manager.getOrElse {
        val c = CacheManager.create()
        try {
          c.addCache(projectId)
        } catch {
          case x: ObjectExistsException =>
            c.removeCache(projectId)
            c.addCache(projectId)
        }
        initialized = true
        manager = Some(c)
        c
      }
    }
  }

  protected[this] def cache = mgr.getCache(projectId)

  private val timeout = {
    import scala.concurrent.duration._
    4.hours.toSeconds.toInt
  }

  def keys() = {
    import scala.collection.JavaConverters._
    cache.getKeys.asScala.map({
      case s: String => s
      case x => x.toString
    })
  }

  private def set(key: String, value: Any, expiration: Int): Unit = {
    val element = new Element(key, value)
    if (expiration == 0) element.setEternal(true)
    element.setTimeToLive(expiration)
    cache.put(element)
  }

  def set(key: String, value: Any): Unit = {
    set(key, value, timeout)
  }

  def get(key: String): Option[Any] = {
    Option(cache.get(key)).map(_.getObjectValue)
  }

  def getAs[T](key: String)(implicit ct: ClassTag[T]): Option[T] = get(key).flatMap { item =>
    if (TypeUtils.isInstance(item, ct.runtimeClass)) {
      item match {
        case t: T => Some(t)
        case _ => None
      }
    } else {
      None
    }
  }

  def remove(key: String) = cache.remove(key)

  def clear() = cache.removeAll()

  def close() = if (initialized) {
    initialized = false
    mgr.removeCache(projectId)
    manager = None
  }
}
