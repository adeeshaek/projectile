package com.kyleu.projectile.models.export.typ

import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.typ.FieldType._

object FieldTypeAsScala {
  def asScala(config: ExportConfiguration, t: FieldType): String = t match {
    case UnitType => "Unit"

    case StringType => "String"
    case EncryptedStringType => "String"

    case BooleanType => "Boolean"
    case ByteType => "Byte"
    case ShortType => "Short"
    case IntegerType => "Int"
    case LongType => "Long"
    case FloatType => "Float"
    case DoubleType => "Double"
    case BigDecimalType => "BigDecimal"

    case DateType => "LocalDate"
    case TimeType => "LocalTime"
    case TimestampType => "LocalDateTime"
    case TimestampZonedType => "ZonedDateTime"

    case RefType => "String"
    case XmlType => "String"
    case UuidType => "UUID"

    case ObjectType(k, _) => k
    case StructType(key) => config.getModelOpt(key).map(_.className).getOrElse(key)

    case EnumType(key) => config.getEnumOpt(key).map(_.className).getOrElse(key)
    case ListType(typ) => s"List[${asScala(config, typ)}]"
    case SetType(typ) => s"Set[${asScala(config, typ)}]"
    case MapType(k, v) => s"Map[${asScala(config, k)}, ${asScala(config, v)}]"

    case UnionType(key, _) => "Json" // TODO: key

    case JsonType => "Json"
    case CodeType => "String"
    case TagsType => "List[Tag]"

    case ByteArrayType => "Array[Byte]"

    // Scala.js
    case AnyType => "js.Dynamic"
    case ExoticType(key) => key match {
      case _ => s"js.Any /* exotic($key) */"
    }

    case _ => throw new IllegalStateException(s"Unhandled type [$t]")

  }
}
