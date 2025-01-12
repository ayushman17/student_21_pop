package ch.epfl.pop.model.objects

import org.scalatest.{FunSuite, Matchers}
import ch.epfl.pop.model.objects.Channel

class ChannelSuite extends FunSuite with Matchers {
  test("Root/Sub channel test (1)") {
    val channel = Channel.rootChannel
    channel.isRootChannel should be(true)
    channel.isSubChannel should be(false)
  }
  test("Root/Sub channel test (2)") {
    def channel = Channel("/root/not/")
    an [IllegalArgumentException] shouldBe thrownBy(channel)
  }

  test("Root/Sub channel test (3)") {
    def channel = Channel(Channel.rootChannelPrefix)
    an [IllegalArgumentException] shouldBe thrownBy(channel)

  }

  test("Root/Sub channel test (4)") {
    def channel = Channel("/root//")
    an [IllegalArgumentException] shouldBe thrownBy(channel)
  }

  test("Empty child channel test") {
    def channel = Channel("")
    an [IllegalArgumentException] shouldBe thrownBy(channel)
  }

  test("Slash empty child channel test") {
    def channel = Channel("" + Channel.SEPARATOR)
    an [IllegalArgumentException] shouldBe thrownBy(channel)
  }

  test("Slash single child channel test") {
    def channel = Channel(Channel.rootChannelPrefix + "channelID")
    val expected = Hash(Base64Data("channelID"))
    noException shouldBe thrownBy(channel)
    channel.extractChildChannel should equal(expected)
  }
  test("Root empty child channel test") {
    def channel = Channel.rootChannel
    val expected = Hash(Base64Data("root"))
    channel.extractChildChannel should equal(expected)
  }
  test("Root + 3 children channel test") {
    def channel = Channel("/root/to/ultra/test")
    val expected = Hash(Base64Data("test"))
    noException shouldBe thrownBy(channel)
    channel.extractChildChannel should equal(expected)
  }
  test("Root + 2 children channel test") {
    def channel = Channel("/root/full/pop")
    val expected = Hash(Base64Data("pop"))
    an [IllegalArgumentException] shouldNot be(thrownBy(channel))
    channel.extractChildChannel should equal(expected)
  }
  test("LaoId extraction channel test") {
    val laoId = "base64_lao_id";
    def channel = Channel(Channel.rootChannelPrefix + Base64Data.encode(laoId))
    val expected = laoId.getBytes()
    noException shouldBe thrownBy(channel)
    channel.decodeSubChannel.get should equal(expected)
  }
  
  test("Real LaoId extraction channel test") {
    val laoId = "mEKXWFCMwb";
    def channel = Channel(Channel.rootChannelPrefix + Base64Data.encode(laoId))
    val expected = laoId.getBytes()

    noException shouldBe thrownBy(channel)
    channel.decodeSubChannel.get should equal(expected)

  }
  test("Bad LaoId: dosn't start with /root/ extraction channel test (1)") {
    val laoId = "/toor/base64_lao_id";
    def channel = Channel(laoId)
     an [IllegalArgumentException] shouldBe thrownBy (channel)
  }

  test("Bad LaoId: not encoded in base64 extraction channel test (2)") {
    val laoId = "base64_lao_id"; // Not encoded in BASE64
    def channel = Channel(Channel.rootChannelPrefix + laoId)
    val expected = None
    noException shouldBe thrownBy(channel)
    channel.decodeSubChannel should equal (expected)
  }
}
