package iot

import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, PostStop, Signal}

object Device {
  def apply(groupId: String, deviceId: String): Behavior[Command] =
    Behaviors.setup(context => new Device(context, groupId, deviceId))

  sealed trait Command

  final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends Command
  final case class RespondTemperature(requestId: Long, deviceId: String, value: Option[Double])

  final case class RecordTemperature(requestId: Long, value: Double, replyTo: ActorRef[TemperatureRecorded])
    extends Command
  final case class TemperatureRecorded(requestId: Long)

  case object Passivate extends Command // To stop a device actor from a test
}

class Device(context: ActorContext[Device.Command], groupId: String, deviceId: String)
  extends AbstractBehavior[Device.Command](context) {

  import Device._

  var lastTemperatureReading: Option[Double] = None

  context.log.info("Device actor {}-{} started", groupId, deviceId)

  override def onMessage(msg: Command): Behavior[Command] = {
    msg match {
      case RecordTemperature(requestId, value, replyTo) =>
        lastTemperatureReading = Some(value)
        replyTo ! TemperatureRecorded(requestId)
        context.log.info("Device actor {}-{} recorded temperature reading {} with request {}", groupId, deviceId, value, requestId)
        this

      case ReadTemperature(requestId, replyTo) =>
        replyTo ! RespondTemperature(requestId, deviceId, lastTemperatureReading)
        this

      case Passivate =>
        Behaviors.stopped
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      context.log.info("Device actor {}-{} stopped", groupId, deviceId)
      this
  }
}
