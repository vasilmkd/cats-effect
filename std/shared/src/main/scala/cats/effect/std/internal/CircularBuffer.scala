package cats.effect.std.internal

import cats.effect.kernel.{GenConcurrent, Ref}
import cats.effect.kernel.syntax.all._
import cats.syntax.all._

private[std] final class CircularBuffer[F[_], A](
    capacity: Int,
    buffer: Vector[Ref[F, Option[A]]],
    sequenceBuffer: Vector[Ref[F, Long]],
    producerIndex: Ref[F, Long],
    consumerIndex: Ref[F, Long]
)(implicit F: GenConcurrent[F, _]) {

  def offer(a: A): F[Boolean] = {
    def cond(pIdx: Long): F[Boolean] =
      producerIndex.access.flatMap {
        case (cur, update) =>
          if (pIdx == cur)
            update(pIdx + 1).map(!_)
          else
            F.pure(true)
      }

    def loop(cIdx: Long): F[Option[(Int, Long)]] =
      producerIndex.get.flatMap { pIdx =>
        val seqOffset = (pIdx % capacity).toInt
        sequenceBuffer(seqOffset).get.flatMap { seq =>
          if (seq < pIdx)
            F.ifM(F.pure(pIdx - capacity >= cIdx))(
              consumerIndex.get.flatMap { newcIdx =>
                if (pIdx - capacity >= newcIdx)
                  F.pure(None)
                else
                  loop(newcIdx)
              },
              loop(cIdx)
            )
          else
            F.ifM(cond(pIdx))(
              loop(cIdx),
              F.pure(Some((seqOffset, pIdx)))
            )
        }
      }

    loop(Long.MinValue).flatMap {
      case Some((seqOffset, pIdx)) =>
        val idx = (pIdx % capacity).toInt
        buffer(idx).set(Some(a)) *> sequenceBuffer(seqOffset).set(pIdx + 1).as(true)
      case None =>
        F.pure(false)
    }.uncancelable
  }

  def poll: F[Option[A]] = {
    def cond(cIdx: Long): F[Boolean] =
      consumerIndex.access.flatMap {
        case (cur, update) =>
          if (cIdx == cur)
            update(cIdx + 1).map(!_)
          else
            F.pure(true)
      }

    def loop(pIdx: Long): F[Option[(Int, Long)]] =
      consumerIndex.get.flatMap { cIdx =>
        val seqOffset = (cIdx % capacity).toInt
        val expectedSeq = cIdx + 1
        sequenceBuffer(seqOffset).get.flatMap { seq =>
          if (seq < expectedSeq)
            F.ifM(F.pure(cIdx >= pIdx))(
              producerIndex.get.flatMap { newpIdx =>
                if (cIdx == newpIdx)
                  F.pure(None)
                else
                  loop(newpIdx)
              },
              loop(pIdx)
            )
          else
            F.ifM(cond(cIdx))(
              loop(pIdx),
              F.pure(Some((seqOffset, cIdx)))
            )
        }
      }

    loop(-1L).flatMap {
      case Some((seqOffset, cIdx)) =>
        val idx = (cIdx % capacity).toInt
        buffer(idx).getAndSet(None) <* sequenceBuffer(seqOffset).set(cIdx + capacity)
      case None => F.pure(none[A])
    }.uncancelable
  }
}

object CircularBuffer {
  def apply[F[_], A](capacity: Int)(implicit F: GenConcurrent[F, _]): F[CircularBuffer[F, A]] =
    F.map4(
      (0 until capacity).toVector.traverse(_ => Ref.of[F, Option[A]](None)),
      (0 until capacity).toVector.traverse(_ => Ref.of[F, Long](0L)),
      Ref.of[F, Long](0L),
      Ref.of[F, Long](0L)
    ) { (buffer, sequenceBuffer, producerIndex, consumerIndex) =>
      new CircularBuffer(capacity, buffer, sequenceBuffer, producerIndex, consumerIndex)
    }
}
