package tech.simter.file.service

import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Component
import reactor.core.publisher.Flux
import reactor.core.publisher.Mono
import reactor.core.publisher.toFlux
import tech.simter.exception.NotFoundException
import tech.simter.file.dao.AttachmentDao
import tech.simter.file.dto.AttachmentDto4Update
import tech.simter.file.dto.AttachmentDto4Zip
import tech.simter.file.dto.AttachmentDtoWithChildren
import tech.simter.file.po.Attachment
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.AsynchronousFileChannel
import java.nio.channels.CompletionHandler
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The attachment service implementation.
 *
 * @author cjw
 * @author RJ
 * @author zh
 */
@Component
class AttachmentServiceImpl @Autowired constructor(
  @Value("\${simter.file.root}") private val fileRootDir: String,
  val attachmentDao: AttachmentDao
) : AttachmentService {
  override fun packageAttachments(outputStream: OutputStream, vararg ids: String): Mono<String> {
    return attachmentDao.findDescendentsZipPath(*ids).collectList()
      .flatMap { reactivePackage(outputStream, it).then(Mono.just(it)) }
      .flatMap { dtos ->
        if (dtos.isNotEmpty()) {
          val att = dtos[0]
          val name = att.origin?.let {
            if (dtos.size == 1 && att.type != ":d") {
              "${att.zipPath!!.split("/")[0]}.${att.type}"
            } else {
              att.zipPath!!.split("/")[0]
            }
          } ?: "root"
          Mono.just("$name.zip")
        } else {
          Mono.empty()
        }
      }
  }

  /**
   * responsive package [dtos] to [outputStream], and return [Mono.empty]
   * @param[bufferLength] The length of each read
   */
  private fun reactivePackage(outputStream: OutputStream, dtos: List<AttachmentDto4Zip>, bufferLength: Int = 1024): Mono<Void> {
    // init zip file
    val zos = ZipOutputStream(outputStream)
    val byteBuffer = ByteBuffer.allocate(bufferLength)
    return dtos.toFlux().concatMap { dto ->
      // write a folder
      if (dto.type == ":d") {
        Mono.defer {
          zos.putNextEntry(ZipEntry("${dto.zipPath!!}/"))
          zos.closeEntry()
          Mono.just(Unit)
        }
      }
      // write a file
      else {
        // init reactiveReadAFileToZip a file and init zip entry
        Mono.defer {
          val channel = AsynchronousFileChannel.open(Paths.get("$fileRootDir/${dto.physicalPath!!}"))
          zos.putNextEntry(ZipEntry("${dto.zipPath!!}.${dto.type!!}"))
          Mono.just(channel)
        }
          // recursive reactiveReadAFileToZip the file many times
          .flatMap {
            reactiveReadFile(channel = it, byteBuffer = byteBuffer, index = 0,
              onRead = { byteBuffer, result ->
                zos.write(byteBuffer.array(), 0, result)
                byteBuffer.clear()
              },
              onComplete = {
                zos.closeEntry()
              })
          }
          // finish reactiveReadAFileToZip the file and finish zip entry
          .then(Mono.defer {
            zos.closeEntry()
            Mono.just(Unit)
          })
      }
    }
      // finish the zip file
      .then(Mono.defer {
        zos.flush()
        zos.close()
        Mono.empty<Void>()
      })
  }

  /**
   * responsive and recursive read a file
   * @param[channel] the file's [AsynchronousFileChannel]
   * @param[byteBuffer] used to buffer
   * @param[index] is file start position from the global
   * @param[onRead] each read data of the callback function
   *   format is (ByteBuffer, Int) -> Unit
   *   [ByteBuffer] is data buffer
   *   [Int] is this read length
   * @param[onComplete] read complete of the callback function
   * @param[onError] read error of the callback function
   *   format is (Throwable) -> Unit
   *   [Throwable] is the error
   * @return[Mono.empty] returns a complete signal from the global
   */
  private fun reactiveReadFile(channel: AsynchronousFileChannel,
                               byteBuffer: ByteBuffer,
                               index: Long,
                               onRead: (ByteBuffer, Int) -> Unit = { _, _ -> },
                               onComplete: () -> Unit = {},
                               onError: (Throwable) -> Unit = {})
    : Mono<Long> {
    return Mono.create<Long> {
      channel.read<Void>(byteBuffer, index, null, object : CompletionHandler<Int, Void> {
        override fun completed(result: Int, attachment: Void?) {
          if (result != -1) {
            onRead(byteBuffer, result)
            it.success(index + result)
          } else {
            onComplete()
            it.success()
          }
        }

        override fun failed(exc: Throwable, attachment: Void?) {
          onError(exc)
          it.error(exc)
        }
      })
    }.flatMap { reactiveReadFile(channel, byteBuffer, it, onRead, onComplete, onError) }
  }

  override fun create(vararg attachments: Attachment): Flux<String> {
    return attachmentDao.save(*attachments).thenMany(attachments.map { it.id }.toFlux())
  }

  override fun findDescendents(id: String): Flux<AttachmentDtoWithChildren> {
    return attachmentDao.findDescendents(id)
  }

  override fun update(id: String, dto: AttachmentDto4Update): Mono<Void> {
    return if (dto.path == null && dto.upperId == null) {
      attachmentDao.update(id, dto.data)
    } else {
      // Changed the file path, need to get the full path before and after the change and move the it
      attachmentDao.getFullPath(id)
        .delayUntil { attachmentDao.update(id, dto.data) }
        .zipWith(attachmentDao.getFullPath(id))
        .map {
          Files.move(Paths.get("$fileRootDir/${it.t1}"), Paths.get("$fileRootDir/${it.t2}"),
            StandardCopyOption.REPLACE_EXISTING)
        }
        .then()
    }
  }

  override fun getFullPath(id: String): Mono<String> {
    return attachmentDao.getFullPath(id)
      .switchIfEmpty(Mono.error(NotFoundException("The attachment $id not exists")))
  }

  override fun get(id: String): Mono<Attachment> {
    return attachmentDao.get(id)
  }

  override fun find(pageable: Pageable): Mono<Page<Attachment>> {
    return attachmentDao.find(pageable)
  }

  override fun find(puid: String, upperId: String?): Flux<Attachment> {
    return attachmentDao.find(puid, upperId)
  }

  override fun save(vararg attachments: Attachment): Mono<Void> {
    return attachmentDao.save(*attachments)
  }

  override fun delete(vararg ids: String): Mono<Void> {
    return attachmentDao.delete(*ids)
  }

}