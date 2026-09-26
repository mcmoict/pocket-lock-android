package com.innoshiftconsult.pocketlock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ProximityReliabilityManagerTest {
    @Test
    public void reliableModeKeepsProximityLogic() {
        assertEquals(ProximityReliability.RELIABLE,
                ProximityReliabilityManager.parseStoredValue("RELIABLE"));
        assertEquals(ProximityDetectionMode.PROXIMITY,
                ProximityReliabilityManager.determineDetectionMode(ProximityReliability.RELIABLE));
    }

    @Test
    public void unavailableSensorUsesFusionFallback() {
        assertEquals(ProximityDetectionMode.SENSOR_FUSION,
                ProximityReliabilityManager.determineDetectionMode(ProximityReliability.UNAVAILABLE));
        assertEquals(ProximityDetectionMode.SENSOR_FUSION,
                ProximityReliabilityManager.determineDetectionMode(ProximityReliability.UNRELIABLE));
    }

    @Test
    public void fusionDetectorRejectsDarkRoomWithoutMotion() {
        PocketSensorFusionDetector detector = new PocketSensorFusionDetector();
        detector.onLight(300f, 0L);
        detector.onLight(0f, 100L);
        detector.onAccelerometer(0f, 0f, 9.81f, 200L);

        assertFalse(detector.shouldLock());
    }

    @Test
    public void fusionDetectorAcceptsDarkAfterBrightTransitionWithMotion() {
        PocketSensorFusionDetector detector = new PocketSensorFusionDetector();
        detector.onLight(200f, 0L);
        detector.onLight(180f, 300L);
        detector.onLight(15f, 700L);
        detector.onLight(2f, 900L);
        detector.onAccelerometer(0.2f, -9.4f, 0.3f, 850L);
        detector.onAccelerometer(0.4f, -9.2f, 0.2f, 1000L);

        assertFalse(detector.shouldLock());

        detector.onAccelerometer(0.3f, -9.3f, 0.2f, 1600L);
        assertTrue(detector.shouldLock());

        detector.onAccelerometer(0.2f, -9.3f, 0.3f, 1900L);
        assertTrue(detector.shouldLock());

        detector.onLight(100f, 2000L);
        assertFalse(detector.shouldLock());
    }

    @Test
    public void fusionDetectorUsesInvertedStableOrientationWithoutLightSensor() {
        PocketSensorFusionDetector detector = new PocketSensorFusionDetector(false);
        detector.onAccelerometer(-1f, 4f, 9f, 0L);
        detector.onAccelerometer(-1f, 4f, 9f, 400L);
        detector.onAccelerometer(9f, -4f, 9f, 800L);
        detector.onAccelerometer(8f, -8f, 1f, 1200L);
        detector.onAccelerometer(4f, -9f, 1f, 1600L);

        assertFalse(detector.shouldLock());

        detector.onAccelerometer(3f, -9f, 1f, 2200L);
        assertTrue(detector.shouldLock());

        detector.onAccelerometer(3f, -9f, 1f, 2800L);
        assertTrue(detector.shouldLock());

        detector.onAccelerometer(0f, 4f, 9f, 3200L);
        assertFalse(detector.shouldLock());
    }
}
