package object helpers {

  def romanYear() = RomanYearGenerator.now()

  /**
    * Converts a number of bytes into a human-readable string
    * such as `2.2 MB` or `8.0 EiB`.
    *
    * @param bytes the number of bytes we want to convert
    * @param si    if true, we use base 10 SI units where 1000 bytes are 1 kB.
    *              If false, we use base 2 IEC units where 1024 bytes are 1 KiB.
    * @return the bytes as a human-readable string
    */
  def humanReadableSize(bytes: Long, si: Boolean = false): String = {
    // See https://en.wikipedia.org/wiki/Byte
    val (baseValue, unitStrings) =
      if (si)
        (1000, Vector("B", "kB", "MB", "GB", "TB", "PB", "EB", "ZB", "YB"))
      else
        (1024, Vector("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"))

    def getExponent(curBytes: Long, baseValue: Int, curExponent: Int = 0): Int =
      if (curBytes < baseValue) curExponent
      else {
        val newExponent = 1 + curExponent
        getExponent(curBytes / (baseValue * newExponent), baseValue, newExponent)
      }

    val exponent = getExponent(bytes, baseValue)
    val divisor = Math.pow(baseValue.toDouble, exponent.toDouble)
    val unitString = unitStrings(exponent)

    // Divide the bytes and show one digit after the decimal point
    f"${bytes / divisor}%.1f $unitString"
  }

}
