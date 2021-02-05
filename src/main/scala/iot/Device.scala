package iot

import akka.actor.typed.{ActorRef, Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object Device {
  sealed trait Command

  final case class ReadTemperature(requestId: Long, replyTo: ActorRef[RespondTemperature]) extends Command
  final case class RespondTemperature(requestId: Long, deviceId: String, value: Option[Double])

  final case class RecordTemperature(requestId: Long, value: Double, replyTo: ActorRef[TemperatureRecorded])
    extends Command
  final case class TemperatureRecorded(requestId: Long)

  case object Passivate extends Command // To stop a device actor from a test

  def apply(groupId: String, deviceId: String): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Device actor {}-{} started", groupId, deviceId)
      processMessages(groupId, deviceId, None)
    }
  }

  def processMessages(groupId: String, deviceId: String, lastTemperatureReading: Option[Double]): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case RecordTemperature(requestId, value, replyTo) =>
          replyTo ! TemperatureRecorded(requestId)
          context.log.info("Device actor {}-{} recorded temperature reading {} with request {}", groupId, deviceId, value, requestId)
          processMessages(groupId, deviceId, Some(value))

        case ReadTemperature(requestId, replyTo) =>
          replyTo ! RespondTemperature(requestId, deviceId, lastTemperatureReading)
          Behaviors.same

        case Passivate =>
          Behaviors.stopped
      }
    }
      .receiveSignal {
        case (context, PostStop) =>
          context.log.info("Device actor {}-{} stopped", groupId, deviceId)
          Behaviors.same
      }
  }
}
