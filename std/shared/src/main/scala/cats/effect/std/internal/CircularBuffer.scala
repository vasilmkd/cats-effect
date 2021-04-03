package cats.effect.std.internal

import cats.effect.kernel.{GenConcurrent, Ref}
import cats.syntax.all._

private[std] final class CircularBuffer[F[_], A](
  capacity: Int,
  buffer: Array[Ref[F, A]],
  sequenceBuffer: Array[Ref[F, Long]],
  producerIndex: Ref[F, Long],
  consumerIndex: Ref[F, Long]
)(implicit F: GenConcurrent[F, _]) {
  
  def offer(a: A): F[Boolean] = {

    def cond(seq: Long, pIdx: Long): F[Boolean] =
      if (seq > pIdx)
        F.pure(true)
      else
        producerIndex.access.flatMap { case (cur, update) =>
          if (pIdx == cur)
            update(pIdx + 1).map(!_)
          else
            F.pure(true)
        }

    def loop(cIdx: Long): F[Option[(Int, Long)]] =
      producerIndex
        .get
        .flatMap { pIdx =>
          val seqOffset = (pIdx % capacity).toInt
          sequenceBuffer(seqOffset)
            .get
            .flatMap { seq =>
              if (seq < pIdx)
                F.ifM(F.pure(pIdx - capacity >= cIdx))(
                  consumerIndex
                    .get
                    .flatMap { newcIdx =>
                      if (pIdx - capacity >= cIdx)
                        F.pure(None)
                      else {
                        loop(newcIdx)
                      }
                    },
                  loop(cIdx)
                )
              else
                F.ifM(cond(seq, pIdx))(
                  loop(cIdx),
                  F.pure(Some((seqOffset, pIdx)))
                )
            }
        }

    loop(Long.MinValue).flatMap {
      case Some((seqOffset, pIdx)) =>
        val idx = (pIdx % capacity).toInt
        buffer(idx).set(a) *> sequenceBuffer(seqOffset).set(pIdx + 1).as(true)
      case None =>
        F.pure(false)
    }
  } 
}
