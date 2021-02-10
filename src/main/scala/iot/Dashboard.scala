package iot

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import iot.DeviceManager.{RequestAllTemperatures, TemperatureReading}

import scala.concurrent.duration.FiniteDuration

object Dashboard {

  trait Command

  private case object CollectDeviceTemperatures extends Command

  final case class WrappedRespondAllTemperatures(response: DeviceManager.RespondAllTemperatures) extends Command

  final case class RequestLastTemperatureReport(requestId: Long, replyTo: ActorRef[RespondLastTemperatureReport])
    extends Command
  final case class RespondLastTemperatureReport(
                                          requestId: Long,
                                          dashboardId: String,
                                          deviceTemperatures: Map[String, TemperatureReading])

  def apply(
             deviceManager: ActorRef[DeviceManager.Command],
             deviceGroupId: String,
             dashboardId: String,
             pollingPeriod: FiniteDuration): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Dashboard actor {}-{} started", deviceGroupId, dashboardId)
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(CollectDeviceTemperatures, pollingPeriod)
        processMessages(deviceManager, deviceGroupId, dashboardId, Map.empty[String, TemperatureReading])
      }
    }
  }

  def processMessages(
                       deviceManager: ActorRef[DeviceManager.Command],
                       deviceGroupId: String,
                       dashboardId: String,
                       deviceTemperatures: Map[String, TemperatureReading]): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case CollectDeviceTemperatures =>
          val respondAllTemperaturesAdapter = context.messageAdapter(WrappedRespondAllTemperatures.apply)
          deviceManager ! RequestAllTemperatures(0, deviceGroupId, respondAllTemperaturesAdapter)
          Behaviors.same

        case WrappedRespondAllTemperatures(response) =>
          processMessages(deviceManager, deviceGroupId, dashboardId, response.temperatures)

        case RequestLastTemperatureReport(requestId, replyTo) =>
          replyTo ! RespondLastTemperatureReport(requestId, dashboardId, deviceTemperatures)
          Behaviors.same
      }
    }
  }
}
