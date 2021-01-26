package iot

import akka.actor.typed.ActorSystem

object IotApp extends App {
  // Create ActorSystem and top level supervisor
  ActorSystem[Nothing](IotSupervisor(), "iot-system")
}
