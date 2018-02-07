package tech.simter.file.dao

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.repository.Repository
import org.springframework.data.repository.reactive.ReactiveCrudRepository
import reactor.core.publisher.Mono
import tech.simter.file.po.Attachment

/**
 * Interface for generic CRUD operations on the attachment. See [ReactiveCrudRepository].
 * @author cjw
 */
interface AttachmentDao : Repository<Attachment, String> {
  /**
   *  Retrieves an [Attachment] by its id.
   *
   *  @param id the id for matching.
   *  @return [Mono] emitting the [Attachment] with the given id or [Mono.empty] if none found.
   */
  fun findById(id: String): Mono<Attachment>

  /**
   * Returns a [Page] of [Attachment]'s meeting the paging restriction provided in the [Pageable] object.
   *
   * @param pageable pageable options
   * @return [Mono] emitting a page of Attachments
   */
  fun findAll(pageable: Pageable): Mono<Page<Attachment>>

  /**
   * Save a given [Attachment].
   *
   * @param entity the attachment to save
   * @return [Mono] emitting the saved attachment
   */
  fun save(entity: Attachment): Mono<Attachment>
}