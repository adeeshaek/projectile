package com.kyleu.projectile.web.controllers.project

import com.kyleu.projectile.models.feature.ModelFeature
import com.kyleu.projectile.models.project.member.{MemberOverride, ModelMember}
import com.kyleu.projectile.util.StringUtils
import com.kyleu.projectile.web.controllers.ProjectileController
import com.kyleu.projectile.web.util.ControllerUtils

import scala.concurrent.Future

@javax.inject.Singleton
class ProjectModelController @javax.inject.Inject() () extends ProjectileController {
  def detail(key: String, model: String) = Action.async { implicit request =>
    val p = projectile.getProject(key)
    val i = p.getInput

    val m = p.getModel(model)
    val em = i.model(model)
    val updated = em.apply(m)
    val fin = updated.copy(fields = em.fields.map(f => updated.getFieldOpt(f.key).getOrElse(f)))

    Future.successful(Ok(com.kyleu.projectile.web.views.html.project.member.detailModel(projectile, key, p.toSummary, m, fin)))
  }

  def formNew(key: String) = Action.async { implicit request =>
    val p = projectile.getProject(key)
    val i = p.getInput

    val inputModels = i.models.map(m => (m.key, p.models.exists(x => x.key == m.key)))
    Future.successful(Ok(com.kyleu.projectile.web.views.html.project.member.formNewModel(projectile, key, inputModels)))
  }

  def add(key: String, modelKey: String) = Action.async { _ =>
    val p = projectile.getProject(key)
    val i = p.getInput
    modelKey match {
      case "all" =>
        val toSave = i.models.flatMap {
          case m if p.getModelOpt(m.key).isDefined => None
          case m => Some(ModelMember(pkg = m.pkg, key = m.key, features = p.defaultModelFeatures.map(ModelFeature.withValue)))
        }
        val saved = projectile.saveModelMembers(key, toSave)
        val redir = Redirect(com.kyleu.projectile.web.controllers.project.routes.ProjectController.detail(key))
        Future.successful(redir.flashing("success" -> s"Added ${saved.size} models"))
      case _ =>
        val orig = i.model(modelKey)
        val m = ModelMember(pkg = orig.pkg, key = modelKey, features = p.defaultModelFeatures.map(ModelFeature.withValue))
        projectile.saveModelMember(key, m)
        val redir = Redirect(com.kyleu.projectile.web.controllers.project.routes.ProjectModelController.detail(key, m.key))
        Future.successful(redir.flashing("success" -> s"Added model [${m.key}]"))
    }
  }

  def save(key: String, modelKey: String) = Action.async { implicit request =>
    val p = projectile.getProject(key)
    val m = p.getModel(modelKey)

    val i = p.getInput
    val model = i.model(m.key)

    val form = ControllerUtils.getForm(request.body)

    val nameOverrides = Seq(
      form("propertyName") match {
        case x if x.nonEmpty && x != model.propertyName => Some(MemberOverride("propertyName", x))
        case _ => None
      },
      form("className") match {
        case x if x.nonEmpty && x != model.className => Some(MemberOverride("className", x))
        case _ => None
      },
      form("title") match {
        case x if x.nonEmpty && x != model.title => Some(MemberOverride("title", x))
        case _ => None
      },
      form("plural") match {
        case x if x.nonEmpty && x != model.plural => Some(MemberOverride("plural", x))
        case _ => None
      }
    ).flatten

    val fieldOverrides = model.fields.flatMap { f =>
      Seq(
        form.getOrElse(s"field-${f.key}-propertyName", "") match {
          case x if x.nonEmpty && x != f.propertyName => Some(MemberOverride(s"${f.key}.propertyName", x))
          case _ => None
        },
        form.getOrElse(s"field-${f.key}-title", "") match {
          case x if x.nonEmpty && x != f.title => Some(MemberOverride(s"${f.key}.title", x))
          case _ => None
        },
        form.getOrElse(s"field-${f.key}-type", "") match {
          case x if x.nonEmpty && x != f.t.value => Some(MemberOverride(s"${f.key}.type", x))
          case _ => None
        },
        form.getOrElse(s"field-${f.key}-search", "false") match {
          case x if x.toBoolean != f.inSearch => Some(MemberOverride(s"${f.key}.search", x))
          case _ => None
        }
      ).flatten
    }

    val foreignKeyOverrides = model.foreignKeys.flatMap { fk =>
      form.getOrElse(s"fk-${fk.name}-propertyName", "") match {
        case x if x.nonEmpty && x != fk.name => Some(MemberOverride(s"fk.${fk.name}.propertyName", x))
        case _ => None
      }
    }

    val referenceOverrides = model.references.flatMap { r =>
      form.getOrElse(s"reference-${r.name}-propertyName", "") match {
        case x if x.nonEmpty && x != r.name => Some(MemberOverride(s"reference.${r.name}.propertyName", x))
        case _ => None
      }
    }

    val newMember = m.copy(
      pkg = StringUtils.toList(form("package"), '.'),
      features = StringUtils.toList(form.getOrElse("features", "")).map(ModelFeature.withValue).toSet,
      ignored = StringUtils.toList(form.getOrElse("ignored", "")).toSet,
      overrides = nameOverrides ++ fieldOverrides ++ foreignKeyOverrides ++ referenceOverrides
    )

    projectile.saveModelMembers(key, Seq(newMember))
    val redir = Redirect(com.kyleu.projectile.web.controllers.project.routes.ProjectModelController.detail(key, modelKey))
    Future.successful(redir.flashing("success" -> s"Saved model [$modelKey]"))
  }

  def remove(key: String, member: String) = Action.async { _ =>
    projectile.removeModelMember(key, member)
    Future.successful(Redirect(
      com.kyleu.projectile.web.controllers.project.routes.ProjectController.detail(key)
    ).flashing("success" -> s"Removed model [$member]"))
  }

  def formFeatures(key: String) = Action.async { implicit request =>
    Future.successful(Ok(com.kyleu.projectile.web.views.html.project.form.formModelFeatures(projectile, projectile.getProjectSummary(key))))
  }

  def saveFeatures(key: String) = Action.async { implicit request =>
    val form = ControllerUtils.getForm(request.body)
    val summary = projectile.getProjectSummary(key)
    val features = StringUtils.toList(form("features")).map(ModelFeature.withValue).map(_.value).toSet
    projectile.saveProject(summary.copy(defaultModelFeatures = features))

    Future.successful(Redirect(com.kyleu.projectile.web.controllers.project.routes.ProjectController.detail(summary.key)).flashing(
      "success" -> s"Saved default model features for project [${summary.key}]"
    ))
  }
}
