package app.upmile.location

import app.upmile.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LocationMappingTest {
    @Test fun validFix_mapsLatLon() {
        assertEquals(GeoPoint(37.4979, 127.0276), geoPoint(37.4979, 127.0276))
    }

    @Test fun invalidFix_isDropped() {
        assertNull(geoPoint(Double.NaN, 127.0))
        assertNull(geoPoint(37.0, Double.POSITIVE_INFINITY))
        assertNull(geoPoint(91.0, 127.0))
        assertNull(geoPoint(37.0, -181.0))
        assertNull(geoPoint(0.0, 0.0)) // no-fix placeholder would fake a 300 m+ displacement
    }
}
