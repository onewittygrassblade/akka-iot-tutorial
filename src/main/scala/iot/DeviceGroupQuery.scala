package iot

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  trait Command
  private case object CollectionTimeout extends Command
  final case class WrappedRespondTemperature(
      response: Device.RespondTemperature
  ) extends Command
  private final case class DeviceTerminated(deviceId: String) extends Command

  def apply(
      deviceIdToActor: Map[String, ActorRef[Device.Command]],
      requestId: Long,
      requester: ActorRef[DeviceManager.RespondAllTemperatures],
      timeout: FiniteDuration
  ): Behavior[Command] =
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

        val respondTemperatureAdapter =
          context.messageAdapter(WrappedRespondTemperature.apply)
        deviceIdToActor.foreach {
          case (deviceId, device) =>
            context.watchWith(device, DeviceTerminated(deviceId))
            device ! Device.ReadTemperature(0, respondTemperatureAdapter)
        }

        new DeviceGroupQuery(requestId, requester).processMessages(
          Map.empty[String, DeviceManager.TemperatureReading],
          deviceIdToActor.keySet
        )
      }
    }
}

class DeviceGroupQuery private (
    requestId: Long,
    requester: ActorRef[DeviceManager.RespondAllTemperatures]
) {
  import DeviceGroupQuery._

  private def processMessages(
      repliesSoFar: Map[String, DeviceManager.TemperatureReading],
      stillWaiting: Set[String]
  ): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
      case WrappedRespondTemperature(response) =>
        val reading = response.value match {
          case Some(value) => DeviceManager.Temperature(value)
          case None        => DeviceManager.TemperatureNotAvailable
        }
        val deviceId = response.deviceId
        respondWhenAllCollected(
          requestId,
          requester,
          repliesSoFar + (deviceId -> reading),
          stillWaiting - deviceId
        )

      case DeviceTerminated(deviceId) =>
        if (stillWaiting(deviceId)) {
          respondWhenAllCollected(
            requestId,
            requester,
            repliesSoFar + (deviceId -> DeviceManager.DeviceNotAvailable),
            stillWaiting - deviceId
          )
        }
        respondWhenAllCollected(
          requestId,
          requester,
          repliesSoFar,
          stillWaiting
        )

      case CollectionTimeout =>
        val replies = repliesSoFar ++ stillWaiting.map(deviceId =>
          deviceId -> DeviceManager.DeviceTimedOut
        )
        respondWhenAllCollected(requestId, requester, replies, Set.empty)
    }
  }

  private def respondWhenAllCollected(
      requestId: Long,
      requester: ActorRef[DeviceManager.RespondAllTemperatures],
      repliesSoFar: Map[String, DeviceManager.TemperatureReading],
      stillWaiting: Set[String]
  ): Behavior[Command] = {
    if (stillWaiting.isEmpty) {
      requester ! DeviceManager.RespondAllTemperatures(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      processMessages(repliesSoFar, stillWaiting)
    }
  }
}
