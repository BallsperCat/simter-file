package tech.simter.file.rest.webflux.handler

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED
import org.springframework.http.MediaType.APPLICATION_OCTET_STREAM
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.BodyInserters
import org.springframework.web.reactive.function.server.HandlerFunction
import org.springframework.web.reactive.function.server.RequestPredicate
import org.springframework.web.reactive.function.server.RequestPredicates.POST
import org.springframework.web.reactive.function.server.RequestPredicates.contentType
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.ServerResponse.notFound
import org.springframework.web.reactive.function.server.ServerResponse.ok
import reactor.core.publisher.Mono
import tech.simter.file.service.AttachmentService
import java.io.ByteArrayOutputStream

/**
 * The [HandlerFunction] for package files.
 * Request:
 * ```
 * POST {context-path}/zip?name=:name
 * Content-Type : application/x-www-form-urlencoded
 *
 * id={id1}&id={id2}...
 * ```
 * > {name} is optional
 *
 * Response: (if found)
 * ```
 * 200 OK
 * Content-Type        : application/octet-stream
 * Content-Length      : {len}
 * Content-Disposition : attachment; filename="{name}.zip"
 *
 * {FILE-DATA}
 * ```
 *
 * Response: (if not found)
 * ```
 * 404 Not Found
 * ```
 *
 * @author zh
 */
@Component
class PackageFilesHandler @Autowired constructor(
  @Value("\${simter.file.root}") private val fileRootDir: String,
  private val attachmentService: AttachmentService
) : HandlerFunction<ServerResponse> {

  override fun handle(request: ServerRequest): Mono<ServerResponse> {
    return request.formData().map { it["id"]!! }
      .flatMap {
        val byteOutputStream = ByteArrayOutputStream()
        attachmentService.packageAttachments(byteOutputStream, *it.toTypedArray())
          .map { defaultName ->
            Pair(byteOutputStream.toByteArray(), request.queryParam("name").map { "$it.zip" }.orElse(defaultName))
          }
      }
      .flatMap {
        val data = it.first
        ok().contentType(APPLICATION_OCTET_STREAM)
          .contentLength(data.size.toLong())
          .header("Content-Disposition", "attachment; filename=\"${it.second}\"")
          .body(BodyInserters.fromResource(ByteArrayResource(data)))

      }
      .switchIfEmpty(notFound().build())
  }

  companion object {
    /** The default [RequestPredicate] */
    val REQUEST_PREDICATE: RequestPredicate = POST("/zip").and(contentType(APPLICATION_FORM_URLENCODED))
  }
}