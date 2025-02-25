package com.kyleu.projectile.services.database.query

import java.sql.Types._

import com.kyleu.projectile.models.export.typ.FieldType._
import com.kyleu.projectile.util.Logging
import com.kyleu.projectile.util.tracing.TraceData

object QueryTranslations extends Logging {
  def forType(
    i: Int, n: String, colSize: Option[Int] = None, enums: Seq[com.kyleu.projectile.models.database.schema.EnumType]
  )(implicit td: TraceData) = i match {
    case CHAR | VARCHAR | LONGVARCHAR | CLOB | NCHAR | NVARCHAR | LONGNVARCHAR | NCLOB => enums.find(_.key == n) match {
      case Some(_) => EnumType(n)
      case _ => StringType
    }
    case NUMERIC | DECIMAL => BigDecimalType
    case BIT | BOOLEAN => BooleanType
    case TINYINT => ByteType
    case SMALLINT => IntegerType // TODO ShortType
    case INTEGER | DISTINCT | ROWID => colSize match {
      case Some(x) if x >= 10 => LongType
      case _ => IntegerType
    }
    case BIGINT => LongType
    case REAL | FLOAT => FloatType
    case DOUBLE => DoubleType
    case BINARY | VARBINARY | LONGVARBINARY | BLOB => ByteArrayType
    case DATE => DateType
    case TIME | TIME_WITH_TIMEZONE => TimeType
    case TIMESTAMP if n == "timestamptz" => TimestampZonedType
    case TIMESTAMP => TimestampType
    case TIMESTAMP_WITH_TIMEZONE => TimestampZonedType
    case REF | REF_CURSOR => RefType
    case SQLXML => XmlType
    case OTHER => matchOther(n)

    case NULL => UnitType
    case STRUCT => StructType(n, Nil)
    case ARRAY => n match {
      case _ if n.startsWith("_int4") => ListType(IntegerType)
      case _ if n.startsWith("_int") => ListType(LongType)
      case _ if n.startsWith("_uuid") => ListType(UuidType)
      case _ => ListType(StringType)
    }

    case JAVA_OBJECT =>
      log.warn(s"Encountered object type [$i:$n]")
      StringType

    case _ =>
      log.warn(s"Encountered unknown column type [$i]")
      StringType
  }

  private[this] def matchOther(n: String)(implicit td: TraceData) = n match {
    case "uuid" => UuidType
    case "json" => JsonType
    case "jsonb" => JsonType
    case "hstore" => TagsType
    case "ghstore" => TagsType
    case "internal" => StringType
    case "cstring" => StringType
    case "anyelement" => StringType
    case "record" => StringType
    case "citext" => StringType
    case "inet" => StringType
    case "macaddr" => StringType
    case "trigger" => StringType
    case "interval" => StringType
    case "cidr" => StringType
    case "varbit" => StringType
    case x if x.startsWith("gbtreekey") => StringType
    case x =>
      log.warn(s"Encountered unknown field type [$x]. ")
      StringType
  }
}
