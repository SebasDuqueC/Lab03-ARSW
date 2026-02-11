package edu.eci.arsw.immortals;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

final class ManagerSmokeTest {
  @Test void startsAndStops() throws Exception {
    var m = new ImmortalManager(8, "ordered", 100, 10);
    m.start();
    Thread.sleep(50);
    var report = m.pauseAndReport();
    m.resume();
    m.stop();
    assertEquals(report.expectedTotal(), report.totalHealth());
  }
}
