package com.kyleu.projectile.models.database.input

import com.kyleu.projectile.util.JsonSerializers._

object PostgresConnection {
  val key = "postgres"

  implicit val jsonEncoder: Encoder[PostgresConnection] = deriveEncoder
  implicit val jsonDecoder: Decoder[PostgresConnection] = deriveDecoder
}

case class PostgresConnection(
    host: String = "localhost",
    port: Int = 5432,
    username: String = "",
    password: String = "",
    ssl: Boolean = false,
    db: String = "db",
    catalog: Option[String] = None
)
