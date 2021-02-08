package iot

import akka.actor.typed.Behavior
import akka.actor.typed.scaladsl.Behaviors

object Dashboard {

  trait Command

  def apply(deviceGroupId: String, dashboardId: String): Behavior[Command] = {
    Behaviors.setup { context =>
      context.log.info("Dashboard actor {}-{} started", deviceGroupId, dashboardId)
      processMessages()
    }
  }

  def processMessages(): Behavior[Command] = {
    Behaviors.receive[Command] { (context, message) =>
      message match {
        case _ =>
          Behaviors.same
      }
    }
  }
}
