package iot

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior, PostStop}

import scala.concurrent.duration.DurationInt

object DashboardManager {
  sealed trait Command

  final case class RequestDashboard(deviceGroupId: String, dashboardId: String, replyTo: ActorRef[DashboardRegistered])
    extends Command with Dashboard.Command
  final case class DashboardRegistered(dashboard: ActorRef[Dashboard.Command])

  private final case class DashboardTerminated(deviceGroupId: String, dashboardId: String) extends Command

  def apply(deviceManager: ActorRef[DeviceManager.Command]): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("DashboardManager started")
      processMessages(deviceManager, Map.empty[String, ActorRef[Dashboard.Command]])
    }
  }

  def processMessages(
                       deviceManager: ActorRef[DeviceManager.Command],
                       dashboardIdToActor: Map[String, ActorRef[Dashboard.Command]]): Behavior[Command] =
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case RequestDashboard(deviceGroupId, dashboardId, replyTo) =>
          dashboardIdToActor.get(dashboardId) match {
            case Some(ref) =>
              replyTo ! DashboardRegistered(ref)
              Behaviors.same
            case None =>
              context.log.info("Creating dashboard actor {} for group {}", dashboardId, deviceGroupId)
              val dashboardActor = context.spawn(
                Dashboard(deviceManager, deviceGroupId, dashboardId, 10.seconds),
                s"dashboard-$dashboardId")
              context.watchWith(dashboardActor, DashboardTerminated(deviceGroupId, dashboardId))
              replyTo ! DashboardRegistered(dashboardActor)
              processMessages(deviceManager, dashboardIdToActor + (dashboardId -> dashboardActor.ref))
          }

        case DashboardTerminated(deviceGroupId, dashboardId) =>
          context.log.info("Dashboard actor {}-{} has been terminated", deviceGroupId, dashboardId)
          processMessages(deviceManager, dashboardIdToActor - dashboardId)
      }
    }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info("DashboardManager stopped")
          Behaviors.same
      }
}
