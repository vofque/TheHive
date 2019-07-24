package org.thp.thehive.controllers.v0

import play.api.mvc.{Action, AnyContent, Results}

import javax.inject.{Inject, Singleton}
import org.thp.scalligraph.controllers.{EntryPoint, FieldsParser}
import org.thp.scalligraph.models.{Database, Entity, PagedResult}
import org.thp.scalligraph.query.{ParamQuery, PropertyUpdater, PublicProperty, Query}
import org.thp.thehive.dto.v0.{InputDashboard, OutputDashboard}
import org.thp.thehive.models.Dashboard
import org.thp.thehive.services.{DashboardSrv, DashboardSteps, OrganisationSrv}

@Singleton
class DashboardCtrl @Inject()(entryPoint: EntryPoint, db: Database, dashboardSrv: DashboardSrv, organisationSrv: OrganisationSrv)
    extends QueryableCtrl {

  import DashboardConversion._
  val entityName: String                           = "dashboard"
  val publicProperties: List[PublicProperty[_, _]] = dashboardProperties(dashboardSrv) ::: metaProperties[DashboardSteps]

  val initialQuery: ParamQuery[_] =
    Query.init[DashboardSteps]("listDashboard", (graph, authContext) => organisationSrv.get(authContext.organisation)(graph).dashboards)

  val pageQuery: ParamQuery[_] = Query.withParam[OutputParam, DashboardSteps, PagedResult[Dashboard with Entity]](
    "page",
    FieldsParser[OutputParam],
    (range, dashboardSteps, _) => dashboardSteps.page(range.from, range.to, range.withSize.getOrElse(false))
  )
  val outputQuery: ParamQuery[_] = Query.output[Dashboard with Entity, OutputDashboard]

  def create: Action[AnyContent] =
    entryPoint("create dashboard")
      .extract("dashboard", FieldsParser[InputDashboard])
      .authTransaction(db) { implicit request => implicit graph =>
        val dashboard: InputDashboard = request.body("dashboard")
        organisationSrv.current.getOrFail().map { organisation =>
          val createdDashboard = dashboardSrv.create(dashboard, organisation)
          Results.Created(createdDashboard.toJson)
        }
      }

  def get(dashboardId: String): Action[AnyContent] =
    entryPoint("get dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .get(dashboardId)
          .visible
          .getOrFail()
          .map { dashboard =>
            Results.Ok(dashboard.toJson)
          }
      }

  def update(dashboardId: String): Action[AnyContent] =
    entryPoint("update dashboard")
      .extract("dashboard", FieldsParser.update("dashboard", dashboardProperties(dashboardSrv)))
      .authTransaction(db) { implicit request => implicit graph =>
        val propertyUpdaters: Seq[PropertyUpdater] = request.body("dashboard")
        dashboardSrv
          .update(_.get(dashboardId) /*.can(Permissions.manageAlert)*/, propertyUpdaters) // TODO check permission
          .flatMap { case (dashboardSteps, _) => dashboardSteps.getOrFail() }
          .map(dashboard => Results.Ok(dashboard.toJson))
      }

  def delete(dashboardId: String): Action[AnyContent] =
    entryPoint("delete dashboard")
      .authTransaction(db) { implicit request => implicit graph =>
        dashboardSrv
          .get(dashboardId)
          .delete
          .map(_ => Results.NoContent)

      }
}