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
          if (cur == pIdx)
            update(pIdx + 1)
          else
            F.pure(false)
      }

    def loop: F[Boolean] =
      producerIndex.get.flatMap { pIdx =>
        val seqOffset = (pIdx % capacity).toInt
        sequenceBuffer(seqOffset).get.flatMap { seq =>
          if (seq < pIdx)
            consumerIndex.get.flatMap { cIdx =>
              if (pIdx - capacity >= cIdx)
                F.pure(false)
              else
                loop
            }
          else
            F.ifM(cond(pIdx))(
              buffer(seqOffset)
                .set(Some(a)) *> sequenceBuffer(seqOffset).set(pIdx + 1).as(true),
              loop
            )
        }
      }

    loop
  }

  def poll: F[Option[A]] = {
    def cond(cIdx: Long): F[Boolean] =
      consumerIndex.access.flatMap {
        case (cur, update) =>
          if (cur == cIdx)
            update(cIdx + 1)
          else
            F.pure(false)
      }

    def loop: F[Option[A]] =
      consumerIndex.get.flatMap { cIdx =>
        val seqOffset = (cIdx % capacity).toInt
        val expectedSeq = cIdx + 1
        sequenceBuffer(seqOffset).get.flatMap { seq =>
          if (seq < expectedSeq)
            producerIndex.get.flatMap { pIdx =>
              if (cIdx == pIdx)
                F.pure(None)
              else
                loop
            }
          else
            F.ifM(cond(cIdx))(
              buffer(seqOffset).getAndSet(None) <* sequenceBuffer(seqOffset).set(
                cIdx + capacity),
              loop
            )
        }
      }

    loop
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
