package com.kyleu.projectile.models.cli

import enumeratum.{Enum, EnumEntry}
import com.kyleu.projectile.models.command.ProjectileCommand
import com.kyleu.projectile.models.input.InputSummary
import com.kyleu.projectile.models.project.{ProjectSummary, ProjectTemplate}
import com.kyleu.projectile.services.project.ProjectExampleService
import com.kyleu.projectile.util.{StringUtils, Version}
import org.backuity.clist.{Command, arg, opt}

sealed trait CommandLineAction extends EnumEntry { this: Command =>
  var verbose = opt[Boolean](abbrev = "v", description = "When set, logs way too much information", default = false)
  var dir = opt[String](description = "Working directory, defaults to \".\"", default = ".")

  def toCommand: ProjectileCommand
}

object CommandLineAction extends Enum[CommandLineAction] {
  object Init extends Command(name = "init", description = "Creates the config directory and required files, if missing") with CommandLineAction {
    override def toCommand = ProjectileCommand.Init
  }

  // Common tasks
  object InputRefresh extends Command(name = "refresh", description = "Refreshes the provided input (or all)") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.InputRefresh(key)
  }
  object ProjectUpdate extends Command(name = "update", description = "Updates the provided project (or all)") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.ProjectUpdate(key)
  }
  object ProjectExport extends Command(name = "export", description = "Exports the provided project (or all)") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.ProjectExport(key)
  }
  object Audit extends Command(name = "audit", description = "Audits the provided project (or all)") with CommandLineAction {
    override def toCommand = ProjectileCommand.Audit
  }
  object Codegen extends Command(name = "codegen", description = "Generates code for the provided projects (or all)") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.Codegen(key.toSeq.flatMap(StringUtils.toList(_)))
  }

  // Inputs
  object InputList extends Command(name = "input", description = "Lists summaries of inputs, or details if key provided") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.Inputs(key)
  }
  object InputAdd extends Command(name = "input-add", description = "Add an input to the system") with CommandLineAction {
    var key = arg[String]()
    var title = opt[Option[String]](description = "Optional title for this input")
    var desc = opt[Option[String]](description = "Optional description for this input")
    override def toCommand = ProjectileCommand.InputAdd(InputSummary(key = key, description = desc.getOrElse("")))
  }

  // Examples
  private[this] val newMsg = s"Creates a new project from a template, one of [${ProjectExampleService.projects.map(_.key).mkString(", ")}]"
  object New extends Command(name = "new", description = newMsg) with CommandLineAction {
    var key = arg[String]()
    var template = arg[String]()
    var force = opt[Option[Boolean]](description = "When set, overwrites files")
    override def toCommand = ProjectileCommand.CreateExample(key, template, force.getOrElse(false))
  }

  // Projects
  object ProjectList extends Command(name = "project", description = "Lists summaries of Projectile projects, or details of key") with CommandLineAction {
    var key = arg[Option[String]](required = false)
    override def toCommand = ProjectileCommand.Projects(key)
  }
  object ProjectAdd extends Command(name = "project-add", description = "Adds a project to the system") with CommandLineAction {
    var key = arg[String](description = "Project key for the newly-created project")
    var input = arg[String](description = "Input key to use for this project")
    var title = opt[Option[String]](description = "Optional title for this project")
    var desc = opt[Option[String]](description = "Optional description for this project")
    var template = opt[Option[String]](description = s"Template to use for for this project, one of [${ProjectTemplate.values.mkString(", ")}]")
    def t = template.flatMap(ProjectTemplate.withValueOpt).getOrElse(ProjectTemplate.Custom)
    override def toCommand = ProjectileCommand.ProjectAdd(ProjectSummary.newObj(key = key).copy(template = t, input = input, description = desc.getOrElse("")))
  }
  object PackageSet extends Command(name = "package-set", description = s"Sets the export package for the provided item") with CommandLineAction {
    var project = arg[String](description = "Project key for the newly-created project")
    var item = arg[String]()
    var pkg = arg[String]()
    override def toCommand = ProjectileCommand.SetPackage(project, item, pkg)
  }

  object Server extends Command(name = "server", description = "Starts the web application") with CommandLineAction {
    var port = opt[Int](description = "Http port for the server", default = Version.projectPort)
    override def toCommand = ProjectileCommand.ServerStart(port)
  }

  override val values = findValues
  val actions = values.map(_.asInstanceOf[Command with CommandLineAction])
}
