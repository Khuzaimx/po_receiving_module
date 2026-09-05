package com.sellernest.poreceiving.scan.camera

import com.google.mlkit.vision.barcode.common.Barcode

/**
 * §4.3: "Enable exactly these and no others (a narrower set decodes faster
 * and misreads less): Code 128, Code 39, Code 93, Codabar, EAN-13, EAN-8,
 * UPC-A, UPC-E, ITF, QR Code, Data Matrix, PDF417." Matches
 * [com.sellernest.poreceiving.scan.datawedge.DataWedgeConfig]'s enabled
 * decoder list one for one.
 */
val allowedCameraBarcodeFormats = intArrayOf(
    Barcode.FORMAT_CODE_128,
    Barcode.FORMAT_CODE_39,
    Barcode.FORMAT_CODE_93,
    Barcode.FORMAT_CODABAR,
    Barcode.FORMAT_EAN_13,
    Barcode.FORMAT_EAN_8,
    Barcode.FORMAT_UPC_A,
    Barcode.FORMAT_UPC_E,
    Barcode.FORMAT_ITF,
    Barcode.FORMAT_QR_CODE,
    Barcode.FORMAT_DATA_MATRIX,
    Barcode.FORMAT_PDF417,
)
