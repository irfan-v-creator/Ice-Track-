package com.example

import com.example.domain.engine.UnitConversionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class UnitConversionEngineTest {

  @Test
  fun testValidateRatios_rejectsZeroOrNegative() {
    assertThrows(IllegalArgumentException::class.java) {
      UnitConversionEngine.validateRatios(0, 10)
    }
    assertThrows(IllegalArgumentException::class.java) {
      UnitConversionEngine.validateRatios(10, -1)
    }
    assertThrows(IllegalArgumentException::class.java) {
      UnitConversionEngine.validateRatios(-5, -5)
    }
  }

  @Test
  fun testToTotalPieces_calculatesCorrectly() {
    // 1 Box = 10 Pieces, 1 Carton = 20 Boxes = 200 Pieces
    // 2 Cartons = 400 Pieces, 3 Boxes = 30 Pieces, 5 Pieces = 5 Pieces -> Total = 435 Pieces
    val total = UnitConversionEngine.toTotalPieces(
      cartons = 2,
      boxes = 3,
      pieces = 5,
      piecesPerBox = 10,
      boxesPerCarton = 20
    )
    assertEquals(435L, total)
  }

  @Test
  fun testFromTotalPieces_decomposesAccurately() {
    // 435 Pieces with 10 pcs/box and 20 boxes/carton -> 2 Cartons, 3 Boxes, 5 Pieces
    val breakdown = UnitConversionEngine.fromTotalPieces(
      totalPieces = 435L,
      piecesPerBox = 10,
      boxesPerCarton = 20
    )

    assertEquals(2, breakdown.cartons)
    assertEquals(3, breakdown.boxes)
    assertEquals(5, breakdown.pieces)
    assertEquals(435L, breakdown.totalPieces)
  }

  @Test
  fun testFormat_formatsStandardOutput() {
    val formatted = UnitConversionEngine.format(
      totalPieces = 435L,
      piecesPerBox = 10,
      boxesPerCarton = 20
    )
    assertEquals("2 Ctn, 3 Box, 5 Pcs", formatted)
  }
}
