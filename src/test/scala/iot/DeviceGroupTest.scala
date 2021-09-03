package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class DeviceGroupTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DeviceManager.{
    DeviceRegistered,
    ReplyDeviceList,
    RequestAllTemperatures,
    RequestDeviceList,
    RequestTrackDevice,
    RespondAllTemperatures,
    Temperature,
    TemperatureNotAvailable
  }
  import Device.{Passivate, RecordTemperature, TemperatureRecorded}

  "DeviceGroup actor" when {
    "requested to register a device" must {
      "be able to register a new device" in {
        val probe      = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup(groupId = "group"))

        groupActor ! RequestTrackDevice(
          groupId = "group",
          deviceId = "device1",
          replyTo = probe.ref
        )
        val registered1  = probe.receiveMessage()
        val deviceActor1 = registered1.device

        groupActor ! RequestTrackDevice(
          groupId = "group",
          deviceId = "device2",
          replyTo = probe.ref
        )
        val registered2  = probe.receiveMessage()
        val deviceActor2 = registered2.device
        deviceActor1 should !==(deviceActor2)

        // Check that the device actors are working
        val recordProbe = createTestProbe[TemperatureRecorded]()
        deviceActor1 ! RecordTemperature(
          requestId = 0,
          value = 1.0,
          replyTo = recordProbe.ref
        )
        recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
        deviceActor2 ! Device.RecordTemperature(
          requestId = 1,
          value = 2.0,
          replyTo = recordProbe.ref
        )
        recordProbe.expectMessage(Device.TemperatureRecorded(requestId = 1))
      }

      "return the correct actor for already existing devices" in {
        val probe      = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup(groupId = "group"))

        groupActor ! RequestTrackDevice(
          groupId = "group",
          deviceId = "device1",
          replyTo = probe.ref
        )
        val registered1 = probe.receiveMessage()

        groupActor ! RequestTrackDevice(
          groupId = "group",
          deviceId = "device1",
          replyTo = probe.ref
        )
        val registered2 = probe.receiveMessage()

        registered1.device should ===(registered2.device)
      }

      "ignore requests with a wrong groupId" in {
        val probe      = createTestProbe[DeviceRegistered]()
        val groupActor = spawn(DeviceGroup(groupId = "group"))

        groupActor ! RequestTrackDevice(
          groupId = "wrongGroup",
          deviceId = "device1",
          replyTo = probe.ref
        )
        probe.expectNoMessage(500.milliseconds)
      }
    }
  }

  "requested to return the list of devices" must {
    "be able to list active devices" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor      = spawn(DeviceGroup(groupId = "group"))

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device1",
        replyTo = registeredProbe.ref
      )
      registeredProbe.receiveMessage()

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device2",
        replyTo = registeredProbe.ref
      )
      registeredProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      groupActor ! RequestDeviceList(
        requestId = 0,
        groupId = "group",
        replyTo = deviceListProbe.ref
      )
      deviceListProbe.expectMessage(
        ReplyDeviceList(requestId = 0, ids = Set("device1", "device2"))
      )
    }

    "be able to list active devices after one shuts down" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor      = spawn(DeviceGroup(groupId = "group"))

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device1",
        replyTo = registeredProbe.ref
      )
      val registered1 = registeredProbe.receiveMessage()
      val toShutDown  = registered1.device

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device2",
        replyTo = registeredProbe.ref
      )
      registeredProbe.receiveMessage()

      val deviceListProbe = createTestProbe[ReplyDeviceList]()
      groupActor ! RequestDeviceList(
        requestId = 0,
        groupId = "group",
        replyTo = deviceListProbe.ref
      )
      deviceListProbe.expectMessage(
        ReplyDeviceList(requestId = 0, ids = Set("device1", "device2"))
      )

      toShutDown ! Passivate
      registeredProbe.expectTerminated(
        toShutDown,
        registeredProbe.remainingOrDefault
      )

      // using awaitAssert to retry because it might take longer for the groupActor
      // to see the Terminated, that order is undefined
      registeredProbe.awaitAssert {
        groupActor ! RequestDeviceList(
          requestId = 1,
          groupId = "group",
          replyTo = deviceListProbe.ref
        )
        deviceListProbe.expectMessage(
          ReplyDeviceList(requestId = 1, ids = Set("device2"))
        )
      }
    }
  }

  "requested to collect temperatures from all active devices" must {
    "be able to return a collection with a temperature response for each device" in {
      val registeredProbe = createTestProbe[DeviceRegistered]()
      val groupActor      = spawn(DeviceGroup(groupId = "group"))

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device1",
        replyTo = registeredProbe.ref
      )
      val deviceActor1 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device2",
        replyTo = registeredProbe.ref
      )
      val deviceActor2 = registeredProbe.receiveMessage().device

      groupActor ! RequestTrackDevice(
        groupId = "group",
        deviceId = "device3",
        replyTo = registeredProbe.ref
      )
      registeredProbe.receiveMessage()

      // Check that the device actors are working
      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(
        requestId = 0,
        value = 1.0,
        replyTo = recordProbe.ref
      )
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
      deviceActor2 ! RecordTemperature(
        requestId = 1,
        value = 2.0,
        replyTo = recordProbe.ref
      )
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))
      // No temperature for device3

      val allTempProbe = createTestProbe[RespondAllTemperatures]()
      groupActor ! RequestAllTemperatures(
        requestId = 0,
        groupId = "group",
        replyTo = allTempProbe.ref
      )
      allTempProbe.expectMessage(
        RespondAllTemperatures(
          requestId = 0,
          temperatures = Map(
            "device1" -> Temperature(1.0),
            "device2" -> Temperature(2.0),
            "device3" -> TemperatureNotAvailable
          )
        )
      )
    }
  }
}
