package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DeviceTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import Device._

  "Device actor" when {
    "requested to record the temperature" must {
      "reply with a confirmation message" in {
        val recordProbe = createTestProbe[TemperatureRecorded]()
        val deviceActor = spawn(Device(groupId = "group", deviceId = "device"))

        deviceActor ! RecordTemperature(
          requestId = 1,
          value = 21.0,
          replyTo = recordProbe.ref
        )
        recordProbe.expectMessage(TemperatureRecorded(requestId = 1))
      }
    }

    "requested to provide the temperature reading" must {
      "reply with empty reading if no temperature is known" in {
        val readProbe = createTestProbe[RespondTemperature]()
        val deviceActor = spawn(Device(groupId = "group", deviceId = "device"))

        deviceActor ! ReadTemperature(requestId = 1, replyTo = readProbe.ref)
        val response = readProbe.receiveMessage()
        response.requestId should ===(1)
        response.value should ===(None)
      }

      "reply with the temperature value if it is known" in {
        val recordProbe = createTestProbe[TemperatureRecorded]()
        val readProbe = createTestProbe[RespondTemperature]()
        val deviceActor = spawn(Device(groupId = "group", deviceId = "device"))

        deviceActor ! RecordTemperature(
          requestId = 1,
          value = 21.0,
          replyTo = recordProbe.ref
        )
        deviceActor ! ReadTemperature(requestId = 2, replyTo = readProbe.ref)
        val response = readProbe.receiveMessage()
        response.requestId should ===(2)
        response.value should ===(Some(21.0))
      }
    }
  }
}
