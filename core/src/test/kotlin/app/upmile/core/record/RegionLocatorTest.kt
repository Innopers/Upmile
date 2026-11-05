package app.upmile.core.record

import app.upmile.core.model.GeoPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionLocatorTest {
    private fun res(path: String) = javaClass.classLoader!!.getResource(path)!!.readText()

    @Test fun regionLocator_findsContainingPolygon_andNullOutside() {
        val loc = RegionLocator(res("regions_two.geojson"))
        assertEquals("41135", loc.locate(GeoPoint(37.3948, 127.1112))?.code)
        assertEquals("경기 성남시 분당구", loc.locate(GeoPoint(37.3948, 127.1112))?.name)
        assertEquals("11680", loc.locate(GeoPoint(37.50, 127.03))?.code) // MultiPolygon
        assertEquals("서울 강남구", loc.locate(GeoPoint(37.50, 127.03))?.name)
        assertNull(loc.locate(GeoPoint(33.0, 120.0)))
    }

    @Test fun realAsset_locatesBundangAndGangnam() {
        val loc = RegionLocator(res("regions/sigungu.geojson"))
        assertEquals("41135", loc.locate(GeoPoint(37.3948, 127.1112))?.code)
        // 강남역 sits on the 강남구/서초구 border; the data places it in 서초구 (11650)
        assertEquals("11650", loc.locate(GeoPoint(37.4979, 127.0276))?.code)
        assertEquals("경기 성남시 분당구", loc.locate(GeoPoint(37.3948, 127.1112))?.name)
        assertEquals("서울 강남구", loc.locate(GeoPoint(37.5088, 127.0630))?.name)
        assertEquals("11680", loc.locate(GeoPoint(37.5088, 127.0630))?.code) // 삼성역, 강남구
    }
}
