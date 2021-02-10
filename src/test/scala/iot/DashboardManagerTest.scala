package iot

import akka.actor.testkit.typed.scaladsl.ScalaTestWithActorTestKit
import org.scalatest.wordspec.AnyWordSpecLike

class DashboardManagerTest extends ScalaTestWithActorTestKit with AnyWordSpecLike {
  import DashboardManager._

  "DashboardManager actor" when {
    "requested to register a dashboard" must {
      "be able to register a new dashboard" in {
        val probe = createTestProbe[DashboardRegistered]()
        val managerActor = spawn(DeviceManager())
        val dashboardManager = spawn(DashboardManager(managerActor))

        dashboardManager ! RequestDashboard("group1", "dashboard1", probe.ref)
        val registered1 = probe.receiveMessage()
        val dashboard1 = registered1.dashboard

        dashboardManager ! RequestDashboard("group1", "dashboard2", probe.ref)
        val registered2 = probe.receiveMessage()
        val dashboard2 = registered2.dashboard

        dashboard1 should !==(dashboard2)
      }

      "return the correct actor for already existing dashboard" in {
        val probe = createTestProbe[DashboardRegistered]()
        val managerActor = spawn(DeviceManager())
        val dashboardManager = spawn(DashboardManager(managerActor))

        dashboardManager ! RequestDashboard("group1", "dashboard1", probe.ref)
        val registered1 = probe.receiveMessage()
        val dashboard1 = registered1.dashboard

        dashboardManager ! RequestDashboard("group1", "dashboard1", probe.ref)
        val registered2 = probe.receiveMessage()
        val dashboard2 = registered2.dashboard

        dashboard1 should ===(dashboard2)
      }
    }
  }
}
