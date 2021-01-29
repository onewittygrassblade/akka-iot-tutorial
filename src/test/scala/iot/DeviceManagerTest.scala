package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceManagerTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DeviceManager._
  import Device.{RecordTemperature, TemperatureRecorded}

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

  "requested to return the list of devices in a group" must {
    "be able to list active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
      registeredProbe.receiveMessage()

      managerActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
      registeredProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      managerActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set("device1", "device2")))
    }

    "return an empty set if the group does not exist" in {
      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestDeviceList(requestId = 0, groupId = "group", deviceListProbe.ref)
      deviceListProbe.expectMessage(ReplyDeviceList(requestId = 0, Set.empty))
    }
  }

  "requested to collect temperatures from all active devices in a group" must {
    "be able to return a collection with a temperature response for each device" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      // Create 3 device actors
      managerActor ! RequestTrackDevice("group", "device1", registeredProbe.ref)
      val deviceActor1 = registeredProbe.receiveMessage().device

      managerActor ! RequestTrackDevice("group", "device2", registeredProbe.ref)
      val deviceActor2 = registeredProbe.receiveMessage().device

      managerActor ! RequestTrackDevice("group", "device3", registeredProbe.ref)
      registeredProbe.receiveMessage()

      // Record temperatures for devices 1 and 2
      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
      deviceActor2 ! RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))
      // No temperature for device3

      val allTempProbe = createTestProbe[RespondAllTemperatures]()
      managerActor ! RequestAllTemperatures(requestId = 0, groupId = "group", allTempProbe.ref)
      allTempProbe.expectMessage(
        RespondAllTemperatures(
          requestId = 0,
          temperatures =
            Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0), "device3" -> TemperatureNotAvailable)))
    }

    "return an empty map if the group does not exist" in {
      val allTempProbe = createTestProbe[RespondAllTemperatures]()
      val managerActor = spawn(DeviceManager())

      managerActor ! RequestAllTemperatures(requestId = 0, groupId = "group", allTempProbe.ref)
      allTempProbe.expectMessage(RespondAllTemperatures(requestId = 0, temperatures = Map.empty))
    }
  }
}
