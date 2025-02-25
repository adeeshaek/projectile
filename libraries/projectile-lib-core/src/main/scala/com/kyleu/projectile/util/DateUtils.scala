package com.kyleu.projectile.util

import java.text.SimpleDateFormat
import java.time._
import java.time.format.{DateTimeFormatter, DateTimeParseException}

/** Provides ordering, formatting, and common utilities for Local and Zoned `java.time` date classes */
object DateUtils {
  private[this] val isoFmt = format.DateTimeFormatter.ISO_LOCAL_DATE_TIME
  private[this] val isoZonedFmt = format.DateTimeFormatter.ISO_ZONED_DATE_TIME
  private[this] val dateFmt = format.DateTimeFormatter.ofPattern("yyyy-MM-dd")
  private[this] val niceDateFmt = format.DateTimeFormatter.ofPattern("EEEE, MMM dd, yyyy")
  private[this] val timeFmt = format.DateTimeFormatter.ofPattern("HH:mm:ss")

  implicit def localDateOrdering: Ordering[LocalDate] = Ordering.fromLessThan(_ isBefore _)
  implicit def localDateTimeOrdering: Ordering[LocalDateTime] = Ordering.fromLessThan(_ isBefore _)
  implicit def zonedDateTimeOrdering: Ordering[ZonedDateTime] = Ordering.fromLessThan(_ isBefore _)

  def today = LocalDate.now()
  def now = LocalDateTime.now()
  def nowZoned = ZonedDateTime.now()
  def nowMillis = toMillis(now)
  def currentTime = LocalTime.now()

  def toMillisZoned(zdt: ZonedDateTime) = zdt.toInstant.toEpochMilli
  def toMillis(ldt: LocalDateTime) = ldt.atZone(ZoneId.systemDefault).toInstant.toEpochMilli
  def fromMillisZoned(millis: Long) = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault)
  def fromMillis(millis: Long) = fromMillisZoned(millis).toLocalDateTime

  def toIsoString(ldt: LocalDateTime) = isoFmt.format(ldt)
  def fromIsoString(s: String) = LocalDateTime.from(isoFmt.parse(s))

  def toIsoStringZoned(zdt: ZonedDateTime) = isoZonedFmt.format(zdt)
  def fromIsoStringZoned(s: String) = ZonedDateTime.from(isoZonedFmt.parse(s))

  def fromDateString(s: String) = LocalDate.from(dateFmt.parse(s))
  def fromTimeString(s: String) = LocalTime.from(timeFmt.parse(s))

  def niceDate(d: LocalDate) = niceDateFmt.format(d)
  def niceTime(t: LocalTime) = timeFmt.format(t)
  def niceDateTime(dt: LocalDateTime) = s"${niceDate(dt.toLocalDate)} ${niceTime(dt.toLocalTime)} UTC"
  def niceDateTimeZoned(dt: ZonedDateTime) = s"${niceDate(dt.toLocalDate)} ${niceTime(dt.toLocalTime)} ${dt.getZone.getId}"

  private[this] val dtFmtIso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss")
  private[this] val dtFmtMillis = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
  private[this] val dtFmtDefault = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private[this] val dtFmtAmPm = new SimpleDateFormat("yyyy-MM-dd hh:mma")
  private[this] val dtFmtNoSec = new SimpleDateFormat("yyyy-MM-dd HH:mm")
  private[this] val dtFmtStd = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm")
  private[this] val dtFmts = Seq(dtFmtIso, dtFmtMillis, dtFmtDefault, dtFmtAmPm, dtFmtNoSec, dtFmtStd)

  def parseIsoOffsetDateTime(s: String): Option[ZonedDateTime] = {
    try {
      Some(ZonedDateTime.parse(s, DateTimeFormatter.ISO_OFFSET_DATE_TIME))
    } catch {
      case _: DateTimeParseException => None
    }
  }

  def sqlTimestampFromIsoOffsetDateTime(s: String): Option[java.sql.Timestamp] = {
    parseIsoOffsetDateTime(s).map(zdt => new java.sql.Timestamp(zdt.toInstant.toEpochMilli))
  }

  def fromString(s: String) = dtFmts.foldLeft(Option.empty[LocalDateTime]) { (l, r) =>
    l.orElse(try { Some(fromMillis(r.parse(s).getTime)) } catch { case _: java.text.ParseException => None })
  }.getOrElse(throw new IllegalStateException(s"Cannot parse date/time from [$s]"))

  def sqlDateTimeFromString(s: String): java.sql.Timestamp = {
    sqlTimestampFromIsoOffsetDateTime(s).getOrElse(new java.sql.Timestamp(toMillis(fromString(s))))
  }
}
