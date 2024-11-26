package cipher4s

import cipher4s.implicits.*
import javax.crypto.BadPaddingException
import javax.crypto.Cipher
import javax.crypto.IllegalBlockSizeException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey as JSecretKey
import javax.crypto.spec.SecretKeySpec

trait SecretKey[Alg] {
  val algorithm: Alg

  def encrypt[A: Blob](a: A): Encrypted[A]
  def decrypt[A: Deserializable](encrypted: Encrypted[A]): Either[RuntimeException, A]

  def asJava: JSecretKey
}

object SecretKey {
  def genAES256(size: Int = 256): SecretKey[Algorithm.AES256.type] = {
    val keyGen = KeyGenerator.getInstance("AES")
    keyGen.init(size)
    val key = keyGen.generateKey()

    JavaSecretKey(
      algorithm = Algorithm.AES256,
      delegate = key
    )
  }

  def aes256(key: Array[Byte]): Either[IllegalArgumentException, SecretKey[Algorithm.AES256.type]] = {
    for {
      keySpec <-
        try {
          Right(new SecretKeySpec(key, "AES"))
        } catch {
          case e: IllegalArgumentException => Left(e)
        }
    } yield {
      JavaSecretKey(
        algorithm = Algorithm.AES256,
        delegate = keySpec
      )
    }
  }

}

private[cipher4s] case class JavaSecretKey[Alg](
  algorithm: Alg,
  delegate: JSecretKey
) extends SecretKey[Alg] {

  override def encrypt[A: Blob](a: A): Encrypted[A] = {
    val cipher = Cipher.getInstance(delegate.getAlgorithm)
    cipher.init(Cipher.ENCRYPT_MODE, delegate)

    Encrypted[A](cipher.doFinal(a.blob))
  }

  override def decrypt[A: Deserializable](encrypted: Encrypted[A]): Either[RuntimeException, A] = try {
    val cipher = Cipher.getInstance(delegate.getAlgorithm)
    cipher.init(Cipher.DECRYPT_MODE, delegate)

    cipher.doFinal(encrypted.blob).deserialize[A]
  } catch {
    case e: IllegalBlockSizeException => Left(new RuntimeException("Failed to decrypt", e))
    case e: BadPaddingException       => Left(new RuntimeException("Failed to decrypt", e))
  }

  override def asJava: JSecretKey = delegate
}
