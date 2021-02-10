package iot

import akka.actor.typed.{Behavior, PostStop}
import akka.actor.typed.scaladsl.Behaviors

object IotSupervisor {
  def apply(): Behavior[Nothing] =
    Behaviors.setup[Nothing] { context =>
      context.log.info("IoT Application started")

      val deviceManager = context.spawn(DeviceManager(), "device-manager")
      context.spawn(DashboardManager(deviceManager), "dashboard-manager")

      Behaviors.receiveSignal[Nothing] {
          case (context, PostStop) =>
            context.log.info("IoT Application stopped")
            Behaviors.same
        }
    }
}
