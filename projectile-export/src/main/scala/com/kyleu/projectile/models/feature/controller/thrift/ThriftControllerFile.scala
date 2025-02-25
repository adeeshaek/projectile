package com.kyleu.projectile.models.feature.controller.thrift

import com.kyleu.projectile.models.export.{ExportMethod, ExportService}
import com.kyleu.projectile.models.export.config.ExportConfiguration
import com.kyleu.projectile.models.export.typ.FieldTypeAsScala
import com.kyleu.projectile.models.feature.ServiceFeature
import com.kyleu.projectile.models.output.{CommonImportHelper, OutputPath}
import com.kyleu.projectile.models.output.file.ScalaFile

object ThriftControllerFile {
  def export(config: ExportConfiguration, service: ExportService) = {
    val file = ScalaFile(
      path = OutputPath.ServerSource,
      dir = service.pkg :+ "controllers" :+ service.propertyName,
      key = service.className + "Controller"
    )

    config.addCommonImport(file, "Application")

    val i = if (service.features(ServiceFeature.Auth)) {
      CommonImportHelper.get(config, "AuthController")
    } else {
      CommonImportHelper.get(config, "BaseController")
    }

    if (i._1 == Seq("controllers")) {
      file.addImport("_root_" +: i._1, i._2)
    } else {
      file.addImport(i._1, i._2)
    }

    config.addCommonImport(file, "ControllerUtils")
    config.addCommonImport(file, "DateUtils")
    config.addCommonImport(file, "ExecutionContext")
    config.addCommonImport(file, "JsonSerializers", "_")
    config.addCommonImport(file, "ThriftServiceRegistry")
    config.addCommonImport(file, "TraceData")
    file.addImport(Seq("io", "circe"), "Json")
    file.addImport(Seq("scala", "concurrent"), "Future")
    file.addImport(Seq("play", "api", "mvc"), "Call")

    file.add("@javax.inject.Singleton")
    val inject = "@javax.inject.Inject() (override val app: Application)(implicit ec: ExecutionContext)"
    val controller = if (service.features(ServiceFeature.Auth)) { "AuthController" } else { "BaseController" }
    file.add(s"""class ${service.className}Controller $inject extends $controller("${service.className}") {""", 1)
    file.add(s"def svc = ThriftServiceRegistry.${service.propertyName}")
    file.add(s"""private[this] val rc = ${(service.pkg :+ "controllers").mkString(".")}.${service.propertyName}.routes.${service.className}Controller""")
    file.add()
    file.add(s"""def list = withSession("list", ${service.perm("view")}) { implicit request => implicit td =>""", 1)
    val cfg = s"""app.cfg(u = Some(request.identity), "thrift", "${service.key}")"""
    file.add(s"Future.successful(Ok(${(config.viewPackage :+ "html" :+ "admin" :+ "thrift").mkString(".")}.${service.propertyName}($cfg)))")
    file.add("}", -1)

    service.methods.foreach(m => addMethod(service, m, config, file))
    addHelpers(service, file, config)

    file.add("}", -1)
    file
  }

  private[this] def addMethod(svc: ExportService, m: ExportMethod, config: ExportConfiguration, file: ScalaFile) = {
    file.add()

    val defArgs = ThriftControllerArgumentHelper.defaultArgs(m, config)
    file.add(s"""def ${m.name} = getHelper(title = "${m.name}", act = rc.${m.name}(), args = $defArgs)""")

    val argNames = "argNames = Seq(" + m.args.map("\"" + _.key + "\"").mkString(", ") + ")"
    val postCall = s"""def ${m.name}Call = postHelper(title = "${m.name}", act = rc.${m.name}(), $argNames, result = (args, td) => svc.${m.name}("""
    if (m.args.isEmpty) {
      file.add(postCall + ")(td).map(_.asJson))")
    } else {
      file.add(postCall, 1)
      m.args.foreach { arg =>
        arg.addImport(config, file, Nil)
        val argRootType = FieldTypeAsScala.asScala(config, arg.t, isThrift = true)
        val argType = if (arg.required) { argRootType } else { s"Option[$argRootType]" }
        val ex = s"""throw new IllegalStateException(s"[${arg.key}] json [$${args("${arg.key}")}] is not a valid [$argType]")"""
        val comma = if (m.args.lastOption.contains(arg)) { "" } else { "," }
        file.add(s"""${arg.key} = args("${arg.key}").as[$argType].getOrElse($ex)$comma""")
      }
      file.add(")(td).map(_.asJson))", -1)
    }
  }

  private[this] def addHelpers(svc: ExportService, file: ScalaFile, config: ExportConfiguration) = {
    file.add()
    file.add(s"""private[this] val listCall = ("${svc.className}", rc.list())""")
    val cfg = s"""app.cfg(u = Some(request.identity), "thrift", "${svc.key}")"""

    val args = "title: String, act: Call, args: Json"
    file.add(s"""private[this] def getHelper($args) = withSession(title, ${svc.perm("run")}) { implicit request => implicit td =>""", 1)
    file.add("""Future.successful(render {""", 1)
    file.add(s"case Accepts.Html() => Ok(${config.systemViewPackage.mkString(".")}.html.admin.layout.methodCall(", 1)
    file.add(s"title = title, cfg = $cfg, svc = listCall, args = args, act = act, debug = app.config.debug")
    file.add("))", -1)
    file.add("""case Accepts.Json() => Ok(Json.obj("name" -> title.asJson, "arguments" -> args.asJson))""")
    file.add("})", -1)
    file.add("}", -1)

    val postArgs = "title: String, act: Call, argNames: Seq[String], result: (Map[String, Json], TraceData) => Future[Json]"
    file.add(s"""private[this] def postHelper($postArgs) = {""", 1)
    file.add(s"withSession(name, ${svc.perm("run")}) { implicit request => implicit td =>", 1)
    file.add("val started = DateUtils.now")
    file.add("val args = ControllerUtils.jsonArguments(request.body, argNames: _*)")
    file.add("""def ren(res: Option[Json] = None, err: Option[(String, String)] = None) = render {""", 1)
    file.add(s"""case Accepts.Html() => Ok(${config.systemViewPackage.mkString(".")}.html.admin.layout.methodCall(""", 1)
    file.add(s"""title = title, cfg = $cfg, svc = listCall, args = Json.obj(args.toSeq: _*), act = act, result = res, error = err,""")
    file.add("""started = Some(started), completed = Some(DateUtils.now), debug = app.config.debug""")
    file.add("""))""", -1)
    file.add(s"""case Accepts.Json() => Ok(res.getOrElse(Json.obj("status" -> s"Error: $${err.map(_._2).getOrElse("Unknown")}".asJson)))""")
    file.add("}", -1)

    file.add("result(args, td).map(res => ren(res = Some(res))).recover {", 1)
    file.add("case scala.util.control.NonFatal(x) =>", 1)
    file.add("""log.error(s"Error calling thrift method [$title]", x)""")
    file.add("ren(err = Some((x.getClass.getSimpleName, x.getMessage)))")
    file.add("}", -2)

    file.add("}", -1)
    file.add("}", -1)
  }
}
