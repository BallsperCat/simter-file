package tech.simter.file.rest.webflux.handler

import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.http.MediaType.APPLICATION_JSON_UTF8
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig
import org.springframework.test.web.reactive.server.WebTestClient.bindToRouterFunction
import org.springframework.web.reactive.config.EnableWebFlux
import org.springframework.web.reactive.function.server.RouterFunctions.route
import reactor.core.publisher.Flux
import reactor.core.publisher.toFlux
import tech.simter.file.dto.AttachmentDtoWithChildren
import tech.simter.file.rest.webflux.Utils.randomInt
import tech.simter.file.rest.webflux.Utils.randomString
import tech.simter.file.rest.webflux.handler.FindAttechmentDescendentsHandler.Companion.REQUEST_PREDICATE
import tech.simter.file.service.AttachmentService
import java.util.*

/**
 * Test [FindAttechmentDescendentsHandler].
 *
 * @author zh
 */
@SpringJUnitConfig(FindAttechmentDescendentsHandler::class)
@EnableWebFlux
@MockBean(AttachmentService::class)
internal class FindAttechmentDescendentsHandlerTest @Autowired constructor(
  private val service: AttachmentService,
  handler: FindAttechmentDescendentsHandler
) {
  private val client = bindToRouterFunction(route(REQUEST_PREDICATE, handler)).build()
  private val id = UUID.randomUUID().toString()
  private val url = "/attachment/$id/descendent"
  private fun randomAttachmentDtoWithChildren(depth: Int, maxDegree: Int): AttachmentDtoWithChildren {
    return AttachmentDtoWithChildren().apply {
      id = UUID.randomUUID().toString()
      name = randomString("name")
      type = randomString("type")
      size = randomInt().toLong()
      modifier = randomString("modifier")
      if (depth > 0) {
        children = List(randomInt(0, maxDegree)) { randomAttachmentDtoWithChildren(depth - 1, maxDegree) }
      }
    }
  }

  @Test
  fun `Find some`() {
    // mock
    val dtos = List(randomInt(1, 3)) { randomAttachmentDtoWithChildren(1, 2) }
    `when`(service.findDescendents(id)).thenReturn(dtos.toFlux())

    // invoke and verify
    client.get().uri(url).exchange()
      .expectHeader().contentType(APPLICATION_JSON_UTF8)
      .expectStatus().isOk
      .expectBody().apply {
        dtos.forEachIndexed { index, dto ->
          jsonPath("$[$index].id").isEqualTo(dto.id!!)
          jsonPath("$[$index].name").isEqualTo(dto.name!!)
          jsonPath("$[$index].type").isEqualTo(dto.type!!)
          jsonPath("$[$index].size").isEqualTo(dto.size!!)
          jsonPath("$[$index].modifier").isEqualTo(dto.modifier!!)
          dto.children!!.forEachIndexed { childIndex, childDtos ->
            jsonPath("$[$index].children[$childIndex].id").isEqualTo(childDtos.id!!)
            jsonPath("$[$index].children[$childIndex].name").isEqualTo(childDtos.name!!)
            jsonPath("$[$index].children[$childIndex].type").isEqualTo(childDtos.type!!)
            jsonPath("$[$index].children[$childIndex].size").isEqualTo(childDtos.size!!)
            jsonPath("$[$index].children[$childIndex].modifier").isEqualTo(childDtos.modifier!!)
          }
        }
      }
    verify(service).findDescendents(id)
  }

  @Test
  fun `Found nothing`() {
    // mock
    `when`(service.findDescendents(id)).thenReturn(Flux.empty())

    // invoke and verify
    client.get().uri(url).exchange()
      .expectHeader().contentType(APPLICATION_JSON_UTF8)
      .expectStatus().isOk
      .expectBody().jsonPath("$").isEmpty
    verify(service).findDescendents(id)
  }

}