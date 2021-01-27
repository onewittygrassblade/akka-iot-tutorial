package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceManagerTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DeviceManager._

  "DeviceManager actor" when {
    "requested to register a device" must {
      "be able to register a new device" in {
        val probe = createTestProbe[DeviceRegistered]()
        val managerActor = spawn(DeviceManager())

        managerActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered1 = probe.receiveMessage()
        val deviceActor1 = registered1.device

        managerActor ! RequestTrackDevice("group", "device2", probe.ref)
        val registered2 = probe.receiveMessage()
        val deviceActor2 = registered2.device
        deviceActor1 should !==(deviceActor2)
      }

      "return the correct actor for already existing devices" in {
        val probe = createTestProbe[DeviceRegistered]()
        val managerActor = spawn(DeviceManager())

        managerActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered1 = probe.receiveMessage()

        managerActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered2 = probe.receiveMessage()

        registered1.device should ===(registered2.device)
      }
    }
  }
}
