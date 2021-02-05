package iot

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object DeviceManager {
  sealed trait Command

  final case class RequestTrackDevice(groupId: String, deviceId: String, replyTo: ActorRef[DeviceRegistered])
    extends Command with DeviceGroup.Command
  final case class DeviceRegistered(device: ActorRef[Device.Command])

  final case class RequestDeviceList(requestId: Long, groupId: String, replyTo: ActorRef[ReplyDeviceList])
    extends Command with DeviceGroup.Command
  final case class ReplyDeviceList(requestId: Long, ids: Set[String])

  final case class RequestAllTemperatures(requestId: Long, groupId: String, replyTo: ActorRef[RespondAllTemperatures])
    extends Command with DeviceGroup.Command with DeviceGroupQuery.Command
  final case class RespondAllTemperatures(requestId: Long, temperatures: Map[String, TemperatureReading])

  sealed trait TemperatureReading
  final case class Temperature(value: Double) extends TemperatureReading
  case object TemperatureNotAvailable extends TemperatureReading
  case object DeviceNotAvailable extends TemperatureReading
  case object DeviceTimedOut extends TemperatureReading

  private final case class DeviceGroupTerminated(groupId: String) extends Command  // default Terminated only provides ActorRef

  def apply(): Behavior[Command] =
    Behaviors.setup { context =>
      context.log.info("DeviceManager started")
      processMessages(Map.empty[String, ActorRef[DeviceGroup.Command]])
    }

  def processMessages(groupIdToActor: Map[String, ActorRef[DeviceGroup.Command]]): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case trackMsg@RequestTrackDevice(groupId, _, _) =>
          groupIdToActor.get(groupId) match {
            case Some(ref) =>
              ref ! trackMsg
              Behaviors.same
            case None =>
              context.log.info("Creating device group actor for {}", groupId)
              val groupActor = context.spawn(DeviceGroup(groupId), s"group-$groupId")
              context.watchWith(groupActor, DeviceGroupTerminated(groupId))
              groupActor ! trackMsg
              processMessages(groupIdToActor + (groupId -> groupActor))
          }

        case req@RequestDeviceList(requestId, groupId, replyTo) =>
          groupIdToActor.get(groupId) match {
            case Some(ref) =>
              ref ! req
            case None =>
              replyTo ! ReplyDeviceList(requestId, Set.empty)
          }
          Behaviors.same

        case req@RequestAllTemperatures(requestId, groupId, replyTo) =>
          groupIdToActor.get(groupId) match {
            case Some(ref) =>
              ref ! req
            case None =>
              replyTo ! RespondAllTemperatures(requestId, Map.empty)
          }
          Behaviors.same

        case DeviceGroupTerminated(groupId) =>
          context.log.info("Device group actor for {} has been terminated", groupId)
          processMessages(groupIdToActor - groupId)
      }
    }
      .receiveSignal {
      case (context, PostStop) =>
        context.log.info("DeviceManager stopped")
        Behaviors.same
    }
  }
}
