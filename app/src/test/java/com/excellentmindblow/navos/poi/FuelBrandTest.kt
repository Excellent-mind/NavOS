package com.excellentmindblow.navos.poi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FuelBrandTest {
    @Test
    fun brandsAreFoundInStationNames() {
        assertEquals(FuelBrand.PETRONAS, FuelBrand.of("Petronas Jalan Tun Razak"))
        assertEquals(FuelBrand.SHELL, FuelBrand.of("Shell Bangsar"))
        assertEquals(FuelBrand.PETRON, FuelBrand.of("PETRON Jalan Klang Lama"))
        assertEquals(FuelBrand.CALTEX, FuelBrand.of("Caltex Cheras"))
        assertEquals(FuelBrand.BHPETROL, FuelBrand.of("BHPetrol Setapak"))
        assertEquals(FuelBrand.BHPETROL, FuelBrand.of("BHP Kepong"))
    }

    @Test
    fun petronasIsNotMistakenForPetronAndWordsMustMatchWhole() {
        assertEquals(FuelBrand.PETRONAS, FuelBrand.of("Stesen Minyak Petronas"))
        assertNull(FuelBrand.of("Shellfish Market"))
        assertNull(FuelBrand.of("Petrol station"))
        assertNull(FuelBrand.of(null))
    }
}
