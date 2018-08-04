import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import java.util.Base64

import javax.crypto.{KeyGenerator, SecretKey}

object EncryptionKey {
  def generate(): String = {
    val secretKey: SecretKey = {
      val keyGenerator = KeyGenerator.getInstance("AES")
      val random = SecureRandom.getInstance("SHA1PRNG")
      keyGenerator.init(256, random)
      keyGenerator.generateKey()
    }

    new String(Base64.getEncoder.encode(secretKey.getEncoded), StandardCharsets.UTF_8)
  }
}
