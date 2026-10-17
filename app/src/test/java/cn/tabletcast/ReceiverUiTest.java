package cn.tabletcast;

import org.junit.Test;
import static org.junit.Assert.*;

public class ReceiverUiTest {
    @Test public void playerUsesThreeQuartersOfWidthAndFullscreenUsesAllOfIt() {
        assertEquals(1200, StageGeometry.playerWidth(1600, false));
        assertEquals(2400, StageGeometry.playerWidth(3200, false));
        assertEquals(1600, StageGeometry.playerWidth(1600, true));
        assertEquals(0, StageGeometry.playerWidth(0, false));
        assertEquals(0, StageGeometry.playerWidth(-1, false));
    }
    @Test public void upgradesDefaultNameWithoutOverwritingCustomName() {
        assertEquals("AndroidTV", DeviceNames.forV2(null));
        assertEquals("AndroidTV", DeviceNames.forV2("小米平板 7"));
        assertEquals("AndroidTV", DeviceNames.forV2("AndroidTV"));
        assertEquals("客厅", DeviceNames.forV2("客厅"));
    }
}
