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
import cats.effect.kernel.syntax.monadCancel._
import cats.syntax.all._

import MichaelScottQueue.Node

private[std] final class MichaelScottQueue[F[_], A](
    head: Ref[F, Node[F, A]],
    tail: Ref[F, Node[F, A]]
)(implicit F: GenConcurrent[F, _]) {

  def offer(a: A): F[Unit] =
    F.ref[Option[Node[F, A]]](None)
      .flatMap { next =>
        val node = Node(a, next)

        def loop: F[Unit] =
          tail.get.flatMap { currentTl =>
            currentTl.next.get.flatMap { next =>
              tail.get.flatMap { tl =>
                if (currentTl eq tl) {
                  next match {
                    case Some(next) =>
                      tail.access.flatMap {
                        case (cur, update) =>
                          if (cur eq currentTl)
                            update(next) >> loop
                          else
                            loop
                      }
                    case None =>
                      currentTl.next.access.flatMap {
                        case (cur, update) =>
                          if (cur eq None) {
                            F.ifM(update(Some(node)))(
                              tail.access.flatMap {
                                case (cur, update) =>
                                  if (cur eq currentTl)
                                    update(node).void
                                  else
                                    F.unit
                              },
                              loop
                            )
                          } else {
                            loop
                          }
                      }
                  }
                } else {
                  loop
                }
              }
            }
          }

        loop
      }
      .uncancelable

  def poll: F[Option[A]] = {
    def loop: F[Option[A]] =
      head.get.flatMap { currentHd =>
        tail.get.flatMap { currentTl =>
          currentHd.next.get.flatMap { next =>
            head.get.flatMap { hd =>
              if (currentHd eq hd) {
                if (currentHd eq currentTl) {
                  next match {
                    case Some(next) =>
                      tail.access.flatMap {
                        case (cur, update) =>
                          if (cur eq currentTl)
                            update(next) >> poll
                          else
                            poll
                      }
                    case None =>
                      F.pure(None)
                  }
                } else {
                  head.access.flatMap {
                    case (cur, update) =>
                      if (cur eq currentHd)
                        F.ifM(update(next.get))(
                          F.pure(Some(next.get.value)),
                          poll
                        )
                      else
                        poll
                  }
                }
              } else {
                poll
              }
            }
          }
        }
      }

    loop.uncancelable
  }
}

private object MichaelScottQueue {
  final case class Node[F[_], A](
      value: A,
      next: Ref[F, Option[Node[F, A]]]
  )

  def apply[F[_], A](implicit F: GenConcurrent[F, _]): F[MichaelScottQueue[F, A]] =
    F.ref[Option[Node[F, A]]](None).flatMap { dummyNext =>
      val dummy = Node(null.asInstanceOf[A], dummyNext)
      F.map2(
        F.ref(dummy),
        F.ref(dummy)
      ) { (head, tail) => new MichaelScottQueue(head, tail) }
    }
}
