package iot

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

import scala.concurrent.duration.DurationInt

object DeviceGroup {
  import DeviceManager.{
    DeviceRegistered,
    ReplyDeviceList,
    RequestAllTemperatures,
    RequestDeviceList,
    RequestTrackDevice
  }

  trait Command

  private final case class DeviceTerminated(device: ActorRef[Device.Command], groupId: String, deviceId: String)
    extends Command // default Terminated only provides ActorRef

  def apply(groupId: String): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("DeviceGroup {} started", groupId)
      processMessages(groupId, Map.empty[String, ActorRef[Device.Command]])
    }
  }

  def processMessages(groupId: String, deviceIdToActor: Map[String, ActorRef[Device.Command]]): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case RequestTrackDevice(`groupId`, deviceId, replyTo) =>
          deviceIdToActor.get(deviceId) match {
            case Some(ref) =>
              replyTo ! DeviceRegistered(ref)
              Behaviors.same
            case None =>
              context.log.info("Creating device actor for {}", deviceId)
              val deviceActor = context.spawn(Device(groupId, deviceId), s"device-$deviceId")
              context.watchWith(deviceActor, DeviceTerminated(deviceActor, groupId, deviceId))
              replyTo ! DeviceRegistered(deviceActor)
              processMessages(groupId, deviceIdToActor + (deviceId -> deviceActor))
          }

        case RequestTrackDevice(gId, _, _) =>
          context.log.warn("Ignoring TrackDevice request for {}. This actor is responsible for {}.", gId, groupId)
          Behaviors.same

        case RequestDeviceList(requestId, gId, replyTo) =>
          if (gId == groupId) {
            replyTo ! ReplyDeviceList(requestId, deviceIdToActor.keySet)
            Behaviors.same
          } else
            Behaviors.unhandled

        case RequestAllTemperatures(requestId, gId, replyTo) =>
          if (gId == groupId) {
            context.spawnAnonymous(DeviceGroupQuery(deviceIdToActor, requestId, replyTo, 3.seconds))
            Behaviors.same
          } else {
            Behaviors.unhandled
          }

        case DeviceTerminated(_, _, deviceId) =>
          context.log.info("Device actor for {} has been terminated", deviceId)
          processMessages(groupId, deviceIdToActor - deviceId)
      }
    }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info("DeviceGroup {} stopped", groupId)
          Behaviors.same
      }
  }
}
