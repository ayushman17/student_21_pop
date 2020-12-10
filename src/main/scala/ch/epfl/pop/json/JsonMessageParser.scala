package ch.epfl.pop.json

import ch.epfl.pop.json.JsonCommunicationProtocol._
import ch.epfl.pop.json.JsonMessages._
import ch.epfl.pop.json.JsonUtils.ErrorCodes.ErrorCodes
import ch.epfl.pop.json.JsonUtils.{ErrorCodes, JsonMessageParserError, JsonMessageParserException}
import spray.json._

import scala.util.{Failure, Success, Try}


/**
 * Custom Json Parser for our Json-RPC protocol
 */
object JsonMessageParser {

  /**
   * Parse a Json string into a JsonMessage
   *
   * @param source the input string
   * @return the parsed version of source
   */
  def parseMessage(source: String): Either[JsonMessage, JsonMessageParserError] = {

    def buildJsonMessageParserException(
                                         obj: JsObject,
                                         id: Option[Int] = None,
                                         errorCode: ErrorCodes = ErrorCodes.InvalidData,
                                         description: String = ""): JsonMessageParserError = {

      val msg: String = if (description == "") errorCode.toString else description

      obj.getFields("id") match {
        case Seq(JsNumber(id)) => JsonMessageParserError(msg, Some(id.toInt), errorCode)
        case _ => JsonMessageParserError(msg, None, errorCode)
      }
    }


    Try(source.parseJson.asJsObject) match {
      case Success(obj) =>

        try {
          obj.getFields("jsonrpc") match {
            case Seq(JsString(v)) =>
              if (v != JsonUtils.JSON_RPC_VERSION)
                throw JsonMessageParserException("invalid \"jsonrpc\" field : invalid jsonrpc version")
            case _ => throw DeserializationException("invalid message : jsonrpc field missing or wrongly formatted")
          }

          obj.getFields("method") match {
            case Seq(JsString(m)) => m match {
              /* Subscribing to a channel */
              case Methods.Subscribe() => return Left(obj.convertTo[SubscribeMessageClient])

              /* Unsubscribe from a channel */
              case Methods.Unsubscribe() => return Left(obj.convertTo[UnsubscribeMessageClient])

              /* Propagate message on a channel */
              case Methods.Message() => return Left(obj.convertTo[PropagateMessageServer])

              /* Catch up on past message on a channel */
              case Methods.Catchup() => return Left(obj.convertTo[CatchupMessageClient])

              /* Publish on a channel + All Higher-level communication */
              case Methods.Publish() => return Left(obj.convertTo[JsonMessagePublishClient])

              /* parsing error : invalid method value */
              case _ => throw JsonMessageParserException("invalid message : method value unrecognized")
            }
            case _ =>
          }


          // check that an answer message it either positive (x)or negative
          obj.getFields("result", "error") match {
            case Seq(JsNumber(_), JsObject(_)) =>
              throw JsonMessageParserException("invalid message : an answer cannot have both \"result\" and \"error\" fields")
            case _ =>
          }

          /* parse a positive answer message */
          obj.getFields("result") match {
            case Seq(JsNumber(_)) => return Left(obj.convertTo[AnswerResultIntMessageServer])
            case Seq(JsArray(_)) => return Left(obj.convertTo[AnswerResultArrayMessageServer])
            case _ =>
          }

          /* parse a negative answer message */
          obj.getFields("error") match {
            case Seq(JsObject(_)) => return Left(obj.convertTo[AnswerErrorMessageServer])
            case _ =>
          }

          throw JsonMessageParserException("invalid message : fields missing or wrongly formatted")

        } catch {
          case JsonMessageParserException(msg, id, code) => Right(buildJsonMessageParserException(obj, id, code, msg))
          case DeserializationException(msg, _, _) => Right(buildJsonMessageParserException(obj, description = msg))
          case _ => Right(buildJsonMessageParserException(obj))
        }

      /* parsing error : String is not a valid JsObject */
      case Failure(_) => Right(JsonMessageParserError("Invalid Json formatting (not a valid JsonObject)"))
    }
  }

  def parseChannelMessage(source: Array[Byte]): ChannelMessage = source.map(_.toChar).mkString.parseJson.convertTo[ChannelMessage]


  /**
   * Serialize a JsonMessage into a string
   *
   * @param message the JsonMessage to serialize
   * @return the serialized version of JsonMessage
   */
  @throws(classOf[SerializationException])
  def serializeMessage(message: JsonMessage): String = message match {
    case _: JsonMessageAnswerServer => message match {
      case m: AnswerResultIntMessageServer => m.toJson.toString
      case m: AnswerResultArrayMessageServer => m.toJson.toString
      case m: AnswerErrorMessageServer => m.toJson.toString
      case m: PropagateMessageServer => m.toJson.toString
    }

    case _: JsonMessagePublishClient => message match {
      case m: CreateLaoMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
      case m: UpdateLaoMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
      case m: BroadcastLaoMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
      case m: WitnessMessageMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
      case m: CreateMeetingMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
      case m: BroadcastMeetingMessageClient => m.toJson(JsonMessagePublishClientFormat.write).toString
    }

    case _: JsonMessagePubSubClient => message match {
      case m: SubscribeMessageClient => m.toJson.toString
      case m: UnsubscribeMessageClient => m.toJson.toString
      case m: CatchupMessageClient => m.toJson.toString
    }

    case _ => throw new SerializationException("Json serializer failed : invalid input message")
  }

  def serializeMessage(mc: MessageContent): String = mc.toJson.toString
}
