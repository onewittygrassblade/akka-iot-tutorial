package iot

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.FiniteDuration

object DeviceGroupQuery {
  import DeviceManager.{
    DeviceNotAvailable,
    DeviceTimedOut,
    RespondAllTemperatures,
    Temperature,
    TemperatureNotAvailable,
    TemperatureReading
  }

  trait Command

  private case object CollectionTimeout extends Command

  final case class WrappedRespondTemperature(response: Device.RespondTemperature) extends Command

  private final case class DeviceTerminated(deviceId: String) extends Command

  def apply(
             deviceIdToActor: Map[String, ActorRef[Device.Command]],
             requestId: Long,
             requester: ActorRef[DeviceManager.RespondAllTemperatures],
             timeout: FiniteDuration
           ): Behavior[Command] = {
    Behaviors.setup { context =>
      Behaviors.withTimers { timers =>
        timers.startSingleTimer(CollectionTimeout, CollectionTimeout, timeout)

        val respondTemperatureAdapter = context.messageAdapter(WrappedRespondTemperature.apply)
        deviceIdToActor.foreach {
          case (deviceId, device) =>
            context.watchWith(device, DeviceTerminated(deviceId))
            device ! Device.ReadTemperature(0, respondTemperatureAdapter)
        }

        processMessages(requestId, requester, Map.empty[String, TemperatureReading], deviceIdToActor.keySet)
      }
    }
  }

  def processMessages(
                       requestId: Long,
                       requester: ActorRef[DeviceManager.RespondAllTemperatures],
                       repliesSoFar: Map[String, TemperatureReading],
                       stillWaiting: Set[String]): Behavior[Command] = {
    Behaviors.receiveMessage[Command] {
        case WrappedRespondTemperature(response) =>
          val reading = response.value match {
            case Some(value) => Temperature(value)
            case None => TemperatureNotAvailable
          }
          val deviceId = response.deviceId
          respondWhenAllCollected(requestId, requester, repliesSoFar + (deviceId -> reading), stillWaiting - deviceId)

        case DeviceTerminated(deviceId) =>
          if (stillWaiting(deviceId)) {
            respondWhenAllCollected(requestId, requester, repliesSoFar + (deviceId -> DeviceNotAvailable), stillWaiting - deviceId)
          }
          respondWhenAllCollected(requestId, requester, repliesSoFar, stillWaiting)

        case CollectionTimeout =>
          val replies = repliesSoFar ++ stillWaiting.map(deviceId => deviceId -> DeviceTimedOut)
          respondWhenAllCollected(requestId, requester, replies, Set.empty)
    }
  }

  def respondWhenAllCollected(
                               requestId: Long,
                               requester: ActorRef[DeviceManager.RespondAllTemperatures],
                               repliesSoFar: Map[String, TemperatureReading],
                               stillWaiting: Set[String]): Behavior[Command] = {
    if (stillWaiting.isEmpty) {
      requester ! RespondAllTemperatures(requestId, repliesSoFar)
      Behaviors.stopped
    } else {
      processMessages(requestId, requester, repliesSoFar, stillWaiting)
    }
  }
}
