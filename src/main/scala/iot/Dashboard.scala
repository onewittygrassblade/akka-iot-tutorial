package iot

import akka.actor.typed.receptionist.Receptionist
import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import iot.DeviceManager.{RequestAllTemperatures, TemperatureReading}

import scala.concurrent.duration.{DurationInt, FiniteDuration}

object Dashboard {
  trait Command
  private case object CollectDeviceTemperatures extends Command
  private case class ListingResponse(listing: Receptionist.Listing)
      extends Command
  final case class WrappedRespondAllTemperatures(
      response: DeviceManager.RespondAllTemperatures
  ) extends Command
  final case class RequestLastTemperatureReport(
      requestId: Long,
      replyTo: ActorRef[RespondLastTemperatureReport]
  ) extends Command
  final case class RespondLastTemperatureReport(
      requestId: Long,
      dashboardId: String,
      deviceTemperatures: Map[Long, Map[String, TemperatureReading]]
  )

  private val DefaultPollingPeriod = 10.seconds
  private val DefaultDataRetention = 5

  def apply(
      deviceGroupId: String,
      dashboardId: String,
      pollingPeriod: FiniteDuration = DefaultPollingPeriod,
      dataRetention: Int = DefaultDataRetention
  ): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info(
        "Dashboard actor {}-{} started",
        deviceGroupId,
        dashboardId
      )
      Behaviors.withTimers { timers =>
        timers.startTimerWithFixedDelay(
          CollectDeviceTemperatures,
          pollingPeriod
        )
        new Dashboard(deviceGroupId, dashboardId, dataRetention)
          .processMessages(
            Map.empty[Long, Map[String, TemperatureReading]]
          )
      }
    }
}

class Dashboard private (
    deviceGroupId: String,
    dashboardId: String,
    dataRetention: Int
) {
  import Dashboard._

  private def processMessages(
      deviceTemperatures: Map[Long, Map[String, TemperatureReading]]
  ): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case CollectDeviceTemperatures =>
          context.system.receptionist ! Receptionist.Find(
            DeviceManager.DeviceManagerKey,
            context.messageAdapter[Receptionist.Listing](ListingResponse)
          )
          Behaviors.same

        case ListingResponse(DeviceManager.DeviceManagerKey.Listing(listing)) =>
          if (listing.size > 1)
            context.log.error("More than one DeviceManager actors found")
          else {
            val respondAllTemperaturesAdapter =
              context.messageAdapter(WrappedRespondAllTemperatures.apply)
            listing.head ! RequestAllTemperatures(
              requestId = 0,
              groupId = deviceGroupId,
              replyTo = respondAllTemperaturesAdapter
            )
          }
          Behaviors.same

        case WrappedRespondAllTemperatures(response) =>
          val now =
            System.currentTimeMillis
          val updated = deviceTemperatures + (now -> response.temperatures)
          val limited =
            if (updated.size > dataRetention) updated - updated.keys.min
            else updated
          processMessages(
            limited
          )

        case RequestLastTemperatureReport(requestId, replyTo) =>
          replyTo ! RespondLastTemperatureReport(
            requestId,
            dashboardId,
            deviceTemperatures
          )
          Behaviors.same
      }
    }
  }
}
