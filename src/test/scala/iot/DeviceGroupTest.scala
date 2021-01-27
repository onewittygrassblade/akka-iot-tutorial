package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class DeviceGroupTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DeviceGroup._
  import DeviceManager.{RequestTrackDevice, DeviceRegistered}
  import Device.{RecordTemperature, TemperatureRecorded}

  "DeviceGroup actor" when {
    "requested to register a device" must {
      "be able to register a new device" in {
        val probe = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup("group"))

        groupActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered1 = probe.receiveMessage()
        val deviceActor1 = registered1.device

        groupActor ! RequestTrackDevice("group", "device2", probe.ref)
        val registered2 = probe.receiveMessage()
        val deviceActor2 = registered2.device
        deviceActor1 should !==(deviceActor2)

        // Check that the device actors are working
        val recordProbe = createTestProbe[TemperatureRecorded]()
        deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
        recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
        deviceActor2 ! Device.RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
        recordProbe.expectMessage(Device.TemperatureRecorded(requestId = 1))
      }

      "return the correct actor for already existing devices" in {
        val probe = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup("group"))

        groupActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered1 = probe.receiveMessage()

        groupActor ! RequestTrackDevice("group", "device1", probe.ref)
        val registered2 = probe.receiveMessage()

        registered1.device should ===(registered2.device)
      }

      "ignore requests with a wrong groupId" in {
        val probe = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup("group"))

        groupActor ! RequestTrackDevice("wrongGroup", "device1", probe.ref)
        probe.expectNoMessage(500.milliseconds)
      }
    }
  }
}
