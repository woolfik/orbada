package pl.mpak.usedb.core;

/**
 * ORBADA Fuerteventura: mapa bitowa pol NULL w rekordzie bufora (plik tymczasowy Query).
 * Zastepuje sun.security.util.BitArray (wewnetrzne API JDK) - ten sam uklad bitow:
 * bit i w bajcie i/8, liczac od najstarszego bitu.
 */
final class NullBits {

  private NullBits() {
  }

  static byte[] create(int count) {
    return new byte[(count + 7) / 8];
  }

  static void set(byte[] bits, int index) {
    bits[index / 8] |= (byte)(0x80 >>> (index % 8));
  }

  static boolean get(byte[] bits, int index) {
    return (bits[index / 8] & (0x80 >>> (index % 8))) != 0;
  }

}
