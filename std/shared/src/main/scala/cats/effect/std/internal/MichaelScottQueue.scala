package cats.effect.std.internal

import cats.effect.kernel.{GenConcurrent, Ref}
import cats.syntax.all._

import MichaelScottQueue.Node

private[std] final class MichaelScottQueue[F[_], A](
  head: Ref[F, Node[F, A]],
  tail: Ref[F, Node[F, A]]
)(implicit F: GenConcurrent[F, _]) {

  def offer(a: A): F[Unit] =
    F.ref[Option[Node[F, A]]](None).flatMap { next =>
      val node = Node(a, next)

      def loop: F[Unit] =
        tail.get.flatMap { oldTl =>
          oldTl.next.get.flatMap { next =>
            tail.get.flatMap { tl =>
              if (oldTl eq tl) {
                next match {
                  case None =>
                    oldTl.next.access.flatMap { case (cur, update) =>
                      if (cur eq next)
                        F.ifM(update(Some(node)))(
                          tail.access.flatMap { case (cur, update) =>
                            if (cur eq oldTl)
                              update(node).void
                            else
                              loop
                          },
                          loop
                        )
                      else
                        loop
                    }
                  case Some(nextNode) =>
                    tail.access.flatMap { case (cur, update) =>
                      if (cur eq nextNode)
                        update(oldTl) *> loop
                      else
                        loop
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

  def poll: F[Option[A]] = {
    def loop: F[Option[A]] =
      head.get.flatMap { oldHd =>
        tail.get.flatMap { oldTl =>
          oldHd.next.get.flatMap { next =>
            head.get.flatMap { hd =>
              if (hd eq oldHd) {
                if (oldHd eq oldTl) {
                  next match {
                    case None => F.pure(None)
                    case Some(next) =>
                      tail.access.flatMap { case (cur, update) =>
                      if (cur eq oldTl)
                        update(next) *> loop
                      else
                        loop
                    }
                  }           
                } else {
                  next match {
                    case None => loop
                    case Some(node) =>
                      val a = node.value
                      head.access.flatMap { case (cur, update) =>
                        if (cur eq oldHd)
                          F.ifM(update(node))(
                            F.pure(Some(a)),
                            loop
                          )
                        else
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
      }

    loop
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
      ) { (head, tail) =>
        new MichaelScottQueue(head, tail)
      }
    }
}
