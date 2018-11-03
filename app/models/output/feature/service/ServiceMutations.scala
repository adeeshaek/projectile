package models.output.feature.service

import models.export.ExportModel
import models.export.config.ExportConfiguration
import models.output.feature.Feature
import models.output.file.ScalaFile

object ServiceMutations {
  private[this] val trace = "(implicit trace: TraceData)"

  def mutations(config: ExportConfiguration, model: ExportModel, file: ScalaFile) = {
    if (model.pkFields.nonEmpty) {
      model.pkFields.foreach(_.addImport(config, file, Nil))
      val sig = model.pkFields.map(f => f.propertyName + ": " + f.scalaType(config)).mkString(", ")
      val call = model.pkFields.map(_.propertyName).mkString(", ")
      val interp = model.pkFields.map("$" + _.propertyName).mkString(", ")
      file.addImport(Seq("scala", "concurrent"), "Future")
      file.add()
      file.add(s"def remove(creds: Credentials, $sig)$trace = {", 1)
      file.add(s"""traceF("remove")(td => getByPrimaryKey(creds, $call)(td).flatMap {""", 1)
      file.add(s"case Some(current) =>", 1)
      if (model.features(Feature.Audit)) {
        file.addImport(config.systemPackage ++ Seq("services", "audit"), "AuditHelper")
        val audit = model.pkFields.map(f => f.propertyName + ".toString").mkString(", ")
        file.add(s"""AuditHelper.onRemove("${model.className}", Seq($audit), current.toDataFields, creds)""")
      }
      file.add(s"ApplicationDatabase.executeF(${model.className}Queries.removeByPrimaryKey($call))(td).map(_ => current)")
      file.indent(-1)
      file.add(s"""case None => throw new IllegalStateException(s"Cannot find ${model.className} matching [$interp].")""")
      file.add("})", -1)
      file.add("}", -1)
      file.add()

      file.add(s"def update(creds: Credentials, $sig, fields: Seq[DataField])$trace = {", 1)
      file.add(s"""traceF("update")(td => getByPrimaryKey(creds, $call)(td).flatMap {""", 1)
      file.add(s"""case Some(current) if fields.isEmpty => Future.successful(current -> s"No changes required for ${model.title} [$interp].")""")
      file.add(s"case Some(_) => ApplicationDatabase.executeF(${model.className}Queries.update($call, fields))(td).flatMap { _ =>", 1)
      file.add(s"getByPrimaryKey(creds, $call)(td).map {", 1)
      file.add("case Some(newModel) =>", 1)
      val ids = model.pkFields.map {
        case f if f.notNull => s"""DataField("${f.propertyName}", Some(${f.propertyName}.toString))"""
        case f => s"""DataField("${f.propertyName}", ${f.propertyName}.map(_.toString))"""
      }.mkString(", ")
      if (model.features(Feature.Audit)) {
        file.add(s"""AuditHelper.onUpdate("${model.className}", Seq($ids), newModel.toDataFields, fields, creds)""")
      }
      file.add(s"""newModel -> s"Updated [$${fields.size}] fields of ${model.title} [$interp]."""")
      file.indent(-1)
      file.add(s"""case None => throw new IllegalStateException(s"Cannot find ${model.className} matching [$interp].")""")
      file.add("}", -1)
      file.add("}", -1)
      file.add(s"""case None => throw new IllegalStateException(s"Cannot find ${model.className} matching [$interp].")""")
      file.add("})", -1)
      file.add("}", -1)
    }
  }
}
