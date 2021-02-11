package iot

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit}
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.DurationInt

class DashboardTest extends ScalaTestWithActorTestKit(ManualTime.config) with AnyWordSpecLike {
  import Dashboard._
  import Device.{RecordTemperature, TemperatureRecorded}
  import DeviceManager.{DeviceRegistered, RequestTrackDevice, Temperature, TemperatureNotAvailable, TemperatureReading}

  val manualTime: ManualTime = ManualTime()

  "Dashboard actor" should {
    "periodically collect device temperatures" in {
      val deviceRegisteredProbe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      // Register some devices
      managerActor ! RequestTrackDevice("group1", "device1", deviceRegisteredProbe.ref)
      val deviceActor1 = deviceRegisteredProbe.receiveMessage().device
      managerActor ! RequestTrackDevice("group1", "device2", deviceRegisteredProbe.ref)
      val deviceActor2 = deviceRegisteredProbe.receiveMessage().device
      managerActor ! RequestTrackDevice("group1", "device3", deviceRegisteredProbe.ref)
      deviceRegisteredProbe.receiveMessage()

      // Record temperatures for devices 1 and 2
      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))
      deviceActor2 ! RecordTemperature(requestId = 1, 2.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 1))
      // No temperature for device3

      // Spawn dashboard and require temperature report immediately
      val tempReportProbe = createTestProbe[RespondLastTemperatureReport]()
      val dashboardActor = spawn(Dashboard(managerActor.ref, "group1", "dashboard1", 10.millis, 1))
      dashboardActor ! RequestLastTemperatureReport(0, tempReportProbe.ref)
      tempReportProbe.expectMessage(
        RespondLastTemperatureReport(
          0,
          "dashboard1",
          Map.empty[Long, Map[String, TemperatureReading]]
        )
      )

      // After the dashboard has collected device temperatures
      manualTime.timePasses(11.millis)
      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(1, tempReportProbe.ref)
        // Cannot predict timestamp keys
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(1)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(Map("device1" -> Temperature(1.0), "device2" -> Temperature(2.0), "device3" -> TemperatureNotAvailable))
        )
      }
    }

    "store only the most recent temperature readings" in {
      val deviceRegisteredProbe = createTestProbe[DeviceRegistered]()
      val managerActor = spawn(DeviceManager())

      // Register a device and record a temperature
      managerActor ! RequestTrackDevice("group1", "device1", deviceRegisteredProbe.ref)
      val deviceActor1 = deviceRegisteredProbe.receiveMessage().device

      val recordProbe = createTestProbe[TemperatureRecorded]()
      deviceActor1 ! RecordTemperature(requestId = 0, 1.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 0))

      // Spawn dashboard with retention of 2 data points and gather first temperature reading
      val tempReportProbe = createTestProbe[RespondLastTemperatureReport]()
      val dashboardActor = spawn(Dashboard(managerActor.ref, "group1", "dashboard1", 10.millis, 2))

      manualTime.timePasses(11.millis)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(1, tempReportProbe.ref)
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(1)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(Map("device1" -> Temperature(1.0)))
        )
      }

      // Record new temperature and gather second temperature reading
      deviceActor1 ! RecordTemperature(requestId = 2, 1.5, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 2))

      manualTime.timePasses(11.millis)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(3, tempReportProbe.ref)
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(3)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(Map("device1" -> Temperature(1.0)), Map("device1" -> Temperature(1.5)))
        )
      }

      // Record new temperature and gather third temperature reading
      deviceActor1 ! RecordTemperature(requestId = 4, 2.0, recordProbe.ref)
      recordProbe.expectMessage(TemperatureRecorded(requestId = 4))

      manualTime.timePasses(11.millis)

      tempReportProbe.awaitAssert {
        dashboardActor ! RequestLastTemperatureReport(5, tempReportProbe.ref)
        val response = tempReportProbe.receiveMessage()
        response.requestId should ===(5)
        response.dashboardId should ===("dashboard1")
        response.deviceTemperatures.values.toList should ===(
          List(Map("device1" -> Temperature(1.5)), Map("device1" -> Temperature(2.0)))
        )
      }
    }
  }
}
