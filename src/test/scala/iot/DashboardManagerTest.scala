package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DashboardManagerTest
    extends ScalaTestWithActorTestKit
    with AnyWordSpecLike {
  import DashboardManager._

  private val probe = createTestProbe[DashboardRegistered]()
  spawn(DeviceManager())
  private val dashboardManager = spawn(DashboardManager())

  "DashboardManager actor" when {
    "requested to register a dashboard" must {
      "be able to register a new dashboard" in {
        dashboardManager ! RequestDashboard(
          deviceGroupId = "group1",
          dashboardId = "dashboard1",
          replyTo = probe.ref
        )
        val registered1 = probe.receiveMessage()
        val dashboard1 = registered1.dashboard

        dashboardManager ! RequestDashboard(
          deviceGroupId = "group1",
          dashboardId = "dashboard2",
          replyTo = probe.ref
        )
        val registered2 = probe.receiveMessage()
        val dashboard2 = registered2.dashboard

        dashboard1 should !==(dashboard2)
      }

      "return the correct actor for already existing dashboard" in {
        dashboardManager ! RequestDashboard(
          deviceGroupId = "group1",
          dashboardId = "dashboard1",
          replyTo = probe.ref
        )
        val registered1 = probe.receiveMessage()
        val dashboard1 = registered1.dashboard

        dashboardManager ! RequestDashboard(
          deviceGroupId = "group1",
          dashboardId = "dashboard1",
          replyTo = probe.ref
        )
        val registered2 = probe.receiveMessage()
        val dashboard2 = registered2.dashboard

        dashboard1 should ===(dashboard2)
      }
    }
  }
}
