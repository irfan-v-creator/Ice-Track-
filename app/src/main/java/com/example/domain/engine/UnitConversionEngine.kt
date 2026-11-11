package com.example.domain.engine

import com.example.domain.model.QuantityBreakdown

object UnitConversionEngine {

  class InvalidConversionRatioException(message: String) : IllegalArgumentException(message)

  /**
   * Validates Piece, Box and Carton conversion ratios.
   * Ratios must be strictly positive integers >= 1.
   */
  fun validateRatios(piecesPerBox: Int, boxesPerCarton: Int) {
    if (piecesPerBox <= 0) {
      throw InvalidConversionRatioException("Pieces per Box must be at least 1 (got: $piecesPerBox)")
    }
    if (boxesPerCarton <= 0) {
      throw InvalidConversionRatioException("Boxes per Carton must be at least 1 (got: $boxesPerCarton)")
    }
  }

  /**
   * Converts any combination of Cartons, Boxes, and Pieces into the fundamental base unit: Pieces.
   */
  fun toTotalPieces(
    cartons: Int,
    boxes: Int,
    pieces: Int,
    piecesPerBox: Int,
    boxesPerCarton: Int
  ): Long {
    validateRatios(piecesPerBox, boxesPerCarton)
    require(cartons >= 0) { "Cartons cannot be negative" }
    require(boxes >= 0) { "Boxes cannot be negative" }
    require(pieces >= 0) { "Pieces cannot be negative" }

    val piecesPerCarton = piecesPerBox.toLong() * boxesPerCarton.toLong()
    return (cartons.toLong() * piecesPerCarton) +
           (boxes.toLong() * piecesPerBox.toLong()) +
           pieces.toLong()
  }

  /**
   * Decomposes total fundamental pieces back into canonical Cartons, Boxes, and remaining Pieces.
   */
  fun fromTotalPieces(
    totalPieces: Long,
    piecesPerBox: Int,
    boxesPerCarton: Int
  ): QuantityBreakdown {
    validateRatios(piecesPerBox, boxesPerCarton)
    val isNegative = totalPieces < 0
    val absPieces = kotlin.math.abs(totalPieces)

    val piecesPerCarton = piecesPerBox.toLong() * boxesPerCarton.toLong()
    val cartons = (absPieces / piecesPerCarton).toInt()
    val remainingAfterCartons = absPieces % piecesPerCarton

    val boxes = (remainingAfterCartons / piecesPerBox).toInt()
    val pieces = (remainingAfterCartons % piecesPerBox).toInt()

    val sign = if (isNegative) -1 else 1
    return QuantityBreakdown(
      cartons = cartons * sign,
      boxes = boxes * sign,
      pieces = pieces * sign,
      totalPieces = totalPieces
    )
  }

  /**
   * Formats a total piece count into a human-readable string (e.g. "5 Ctn, 2 Box, 3 Pcs")
   */
  fun format(
    totalPieces: Long,
    piecesPerBox: Int,
    boxesPerCarton: Int
  ): String {
    return fromTotalPieces(totalPieces, piecesPerBox, boxesPerCarton).toFormattedString()
  }
}
