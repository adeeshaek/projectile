package com.kyleu.projectile.models.output

import com.google.common.base.{CaseFormat, Converter}

object ExportHelper {
  private[this] val converters = collection.mutable.HashMap.empty[(CaseFormat, CaseFormat), Converter[String, String]]
  private[this] def getSource(s: String) = s match {
    case _ if s.contains('-') => CaseFormat.LOWER_HYPHEN
    case _ if s.contains('_') => if (s.headOption.exists(_.isUpper)) { CaseFormat.UPPER_UNDERSCORE } else { CaseFormat.LOWER_UNDERSCORE }
    case _ => if (s.headOption.exists(_.isUpper)) { CaseFormat.UPPER_CAMEL } else { CaseFormat.LOWER_CAMEL }
  }

  private[this] def converterFor(src: CaseFormat, tgt: CaseFormat) = converters.getOrElseUpdate(src -> tgt, src.converterTo(tgt))

  def toIdentifier(s: String) = converterFor(getSource(s), CaseFormat.LOWER_CAMEL).convert(s.replaceAllLiterally(" ", "").replaceAllLiterally(".", ""))
  def toClassName(s: String) = if (s.nonEmpty && s == s.toUpperCase) {
    converterFor(getSource(s.toLowerCase), CaseFormat.UPPER_CAMEL).convert(s.toLowerCase.replaceAllLiterally(" ", "").replaceAllLiterally(".", ""))
  } else {
    converterFor(getSource(s), CaseFormat.UPPER_CAMEL).convert(s.replaceAllLiterally(" ", "").replaceAllLiterally(".", ""))
  }
  def toDefaultTitle(s: String) = toClassName(s).flatMap(c => if (c.isUpper) { Seq(' ', c) } else { Seq(c) }).trim

  def toDefaultPlural(str: String) = str match {
    case x if x.endsWith("s") => x
    case x if x.endsWith("y") => x.dropRight(1) + "ies"
    case _ => str + "s"
  }

  val getAllArgs = "orderBy: Option[String] = None, limit: Option[Int] = None, offset: Option[Int] = None"
  val searchArgs = "q: Option[String], orderBy: Option[String] = None, limit: Option[Int] = None, offset: Option[Int] = None"

  private[this] val needsEscaping = Set("abstract", "class", "import", "match", "package", "then", "type")

  private[this] def invalidToken(s: String) = s match {
    case _ if s.contains('.') => true
    case _ if s.contains('-') => true
    case _ if s.matches("^[0-9]") => true
    case _ => false
  }

  def escapeKeyword(s: String) = if (needsEscaping(s) || invalidToken(s)) { "`" + s + "`" } else { s }
}
