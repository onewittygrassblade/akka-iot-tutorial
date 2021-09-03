package iot

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.{DurationInt, FiniteDuration}

class DashboardTest
    extends ScalaTestWithActorTestKit(ManualTime.config)
    with AnyWordSpecLike {
  import Dashboard._
  import Device.{RecordTemperature, TemperatureRecorded}
  import DeviceManager.{DeviceRegistered, RequestTrackDevice, Temperature, TemperatureNotAvailable, TemperatureReading}

  private val manualTime: ManualTime        = ManualTime()
  private val pollingPeriod: FiniteDuration = 10.millis
  private val dataRetention: Int            = 2

  private val managerActor          = spawn(DeviceManager())
  private val deviceRegisteredProbe = createTestProbe[DeviceRegistered]()
  private val recordProbe           = createTestProbe[TemperatureRecorded]()
  private val tempReportProbe       = createTestProbe[RespondLastTemperatureReport]()

  "Dashboard actor" should {
    "periodically collect device temperatures" in {
      // Register some devices
      managerActor ! RequestTrackDevice(
        groupId = "group1",
        deviceId = "device1",
        replyTo = deviceRegisteredProbe.ref
      )
      val deviceActor1 = deviceRegisteredProbe.receiveMessage().device
      managerActor ! RequestTrackDevice(
        groupId = "group1",
        deviceId = "device2",
        replyTo = deviceRegisteredProbe.ref
      )
      val deviceActor2 = deviceRegisteredProbe.receiveMessage().device
      managerActor ! RequestTrackDevice(
        groupId = "group1",
        deviceId = "device3",
        replyTo = deviceRegisteredProbe.ref
      )
      deviceRegisteredProbe.receiveMessage()

      // Record temperatures for devices 1 and 2
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

      // Spawn dashboard and require temperature report immediately
      val dashboardActor =
        spawn(
          Dashboard(
            deviceGroupId = "group1",
            dashboardId = "dashboard1",
            pollingPeriod
          )
        )
      dashboardActor ! RequestLastTemperatureReport(
        requestId = 0,
        replyTo = tempReportProbe.ref
      )
      tempReportProbe.expectMessage(
        RespondLastTemperatureReport(
          requestId = 0,
          dashboardId = "dashboard1",
          deviceTemperatures = Map.empty[Long, Map[String, TemperatureReading]]
        )
      )

      // After the dashboard has collected device temperatures
      manualTime.timePasses(pollingPeriod)
      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(
          requestId = 1,
          replyTo = tempReportProbe.ref
        )
        // Cannot predict timestamp keys
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(1)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(
            Map(
              "device1" -> Temperature(1.0),
              "device2" -> Temperature(2.0),
              "device3" -> TemperatureNotAvailable
            )
          )
        )
      }
    }

    "store only the most recent temperature readings" in {
      // Register a device and record a temperature
      managerActor ! RequestTrackDevice(
        groupId = "group2",
        deviceId = "device1",
        replyTo = deviceRegisteredProbe.ref
      )
      val deviceActor1 = deviceRegisteredProbe.receiveMessage().device

      deviceActor1 ! RecordTemperature(
        requestId = 0,
        value = 1.0,
        replyTo = recordProbe.ref
      )
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))

      // Spawn dashboard with retention of 2 data points and gather first temperature reading
      val dashboardActor =
        spawn(
          Dashboard(
            deviceGroupId = "group2",
            dashboardId = "dashboard1",
            pollingPeriod,
            dataRetention
          )
        )

      manualTime.timePasses(pollingPeriod)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(
          requestId = 1,
          replyTo = tempReportProbe.ref
        )
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(1)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(Map("device1" -> Temperature(1.0)))
        )
      }

      // Record new temperature and gather second temperature reading
      deviceActor1 ! RecordTemperature(
        requestId = 2,
        value = 1.5,
        replyTo = recordProbe.ref
      )
      recordProbe.expectMessage(TemperatureRecorded(requestId = 2))

      manualTime.timePasses(pollingPeriod)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(
          requestId = 3,
          replyTo = tempReportProbe.ref
        )
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(3)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(
            Map("device1" -> Temperature(1.0)),
            Map("device1" -> Temperature(1.5))
          )
        )
      }

      // Record new temperature and gather third temperature reading
      deviceActor1 ! RecordTemperature(
        requestId = 4,
        value = 2.0,
        replyTo = recordProbe.ref
      )
      recordProbe.expectMessage(TemperatureRecorded(requestId = 4))

      manualTime.timePasses(pollingPeriod)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(
          requestId = 5,
          replyTo = tempReportProbe.ref
        )
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(5)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(
            Map("device1" -> Temperature(1.5)),
            Map("device1" -> Temperature(2.0))
          )
        )
      }
    }
  }
}
