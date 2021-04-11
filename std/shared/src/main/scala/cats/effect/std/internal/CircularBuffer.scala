/*
 * Copyright 2020-2021 Typelevel
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
            update(pIdx + 1)
          else
            F.pure(false)
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
              F.pure(Some((seqOffset, pIdx))),
              loop(cIdx)
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
            update(cIdx + 1)
          else
            F.pure(false)
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
              F.pure(Some((seqOffset, cIdx))),
              loop(pIdx)
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

private object CircularBuffer {
  def apply[F[_], A](capacity: Int)(implicit F: GenConcurrent[F, _]): F[CircularBuffer[F, A]] =
    F.map4(
      (0 until capacity).toVector.traverse(_ => Ref.of[F, Option[A]](None)),
      (0 until capacity).toVector.traverse(n => Ref.of[F, Long](n.toLong)),
      F.ref(0L),
      F.ref(0L)
    ) { (buffer, sequenceBuffer, producerIndex, consumerIndex) =>
      new CircularBuffer(capacity, buffer, sequenceBuffer, producerIndex, consumerIndex)
    }
}
