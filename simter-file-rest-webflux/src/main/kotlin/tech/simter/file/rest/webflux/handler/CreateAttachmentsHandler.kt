package tech.simter.file.rest.webflux.handler

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpStatus.CREATED
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.*
import org.springframework.web.reactive.function.server.RequestPredicates.POST
import org.springframework.web.reactive.function.server.RequestPredicates.contentType
import org.springframework.web.reactive.function.server.ServerResponse.status
import reactor.core.publisher.Mono
import tech.simter.file.dto.AttachmentDto4Create
import tech.simter.file.po.Attachment
import tech.simter.file.service.AttachmentService
import java.time.OffsetDateTime
import java.util.*

/**
 * The [HandlerFunction] for Create Attachments .
 *
 * Request:
 *
 * ```
 * POST {context-path}/attachment
 * Content-Type : application/json;charset=UTF-8
 *
 * [{DATA}, ...]
 * ```
 * {DATA}={id, name, upperId, path, type, puid}
 * >
 *  If no path is specified, use ID instead.
 *  The user can specify the id but is responsible for ensuring the global uniqueness of the id.
 *  If type is ":d", the attachment is a folder attachment.
 *  If type is none or blank, the attachment type is not specified.
 * >
 * Response:
 *
 * ```
 * 201 Created
 *
 *  [id1, ..., idN] # The order is the same as [{DATA}, ...]
 * ```
 *
 * @author zh
 */

@Component
class CreateAttachmentsHandler @Autowired constructor(
  private val attachmentService: AttachmentService
) : HandlerFunction<ServerResponse> {
  override fun handle(request: ServerRequest): Mono<ServerResponse> {
    return request.bodyToFlux<AttachmentDto4Create>().map { toAttachment(it) }
      .collectList().map { it.toTypedArray() }
      .flatMap { attachmentService.create(*it).collectList() }
      .flatMap { status(CREATED).contentType(APPLICATION_JSON_UTF8).syncBody(it) }
  }

  fun toAttachment(dto: AttachmentDto4Create): Attachment {
    val id = dto.id ?: UUID.randomUUID().toString()
    val now = OffsetDateTime.now()
    return Attachment(id = id, path = dto.path ?: id, name = dto.name!!, type = dto.type ?: "",
      size = 0, createOn = now, creator = "Simter", modifyOn = now, modifier = "Simter",
      puid = dto.puid ?: "", upperId = dto.upperId ?: "EMPTY")
  }

  companion object {
    /** The default [RequestPredicate] */
    val REQUEST_PREDICATE: RequestPredicate = POST("/attachment").and(contentType(APPLICATION_JSON_UTF8))
  }
}